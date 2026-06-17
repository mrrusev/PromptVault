import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { setToken } from '../api/tokenStore';
import { ROUTES } from '../routes';
import { server } from '../test/server';
import { LoginPage } from './LoginPage';

const API = '*/api';

// Renders the login page plus a stand-in dashboard so we can assert navigation
// on a successful submit.
function renderLogin() {
  return render(
    <MemoryRouter initialEntries={[ROUTES.login]}>
      <AuthProvider>
        <Routes>
          <Route path={ROUTES.login} element={<LoginPage />} />
          <Route path={ROUTES.dashboard} element={<h1>Dashboard Home</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/username/i), 'alice');
  await user.type(screen.getByLabelText(/password/i), 'secret');
  await user.click(screen.getByRole('button', { name: /sign in/i }));
}

afterEach(() => setToken(null));

describe('LoginPage', () => {
  it('logs in and navigates to the dashboard on success', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json({ token: 'jwt-ok', user: { id: 1, username: 'alice' } }),
      ),
    );
    const user = userEvent.setup();
    renderLogin();

    await fillAndSubmit(user);

    expect(await screen.findByRole('heading', { name: /dashboard home/i })).toBeInTheDocument();
  });

  it('shows an error via role="alert" and does not navigate on 401', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 }),
      ),
    );
    const user = userEvent.setup();
    renderLogin();

    await fillAndSubmit(user);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Invalid credentials');
    expect(screen.queryByRole('heading', { name: /dashboard home/i })).not.toBeInTheDocument();
    // Still on the login form.
    expect(screen.getByRole('heading', { name: /sign in to promptvault/i })).toBeInTheDocument();
  });

  it('disables the submit button while the request is pending', async () => {
    let resolveResponse: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => {
      resolveResponse = resolve;
    });
    server.use(
      http.post(`${API}/auth/login`, async () => {
        await gate;
        return HttpResponse.json({ token: 'jwt-ok', user: { id: 1, username: 'alice' } });
      }),
    );
    const user = userEvent.setup();
    renderLogin();

    await fillAndSubmit(user);

    const button = screen.getByRole('button', { name: /signing in/i });
    await waitFor(() => expect(button).toBeDisabled());

    // Release the request and let the flow complete to avoid act warnings.
    resolveResponse?.();
    expect(await screen.findByRole('heading', { name: /dashboard home/i })).toBeInTheDocument();
  });
});
