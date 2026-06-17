import { render, screen } from '@testing-library/react';
import { useEffect } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { App } from './App';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { setToken } from './api/tokenStore';
import { ROUTES } from './routes';

// Logs a user in on mount, then only mounts <App/> once auth state is true.
// This guarantees App's routes commit at the requested location already
// authenticated, instead of the guard redirecting to /login on first paint.
function SeedAuth() {
  const { login, isAuthenticated } = useAuth();
  useEffect(() => {
    login('test-jwt', { id: 1, username: 'alice' });
  }, [login]);
  return isAuthenticated ? <App /> : null;
}

function renderApp(initialPath: string, authenticated: boolean) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>{authenticated ? <SeedAuth /> : <App />}</AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => setToken(null));
afterEach(() => setToken(null));

describe('routing — unauthenticated', () => {
  const protectedPaths = [
    ROUTES.root,
    ROUTES.dashboard,
    ROUTES.collections,
    ROUTES.prompt(42),
  ];

  it.each(protectedPaths)('redirects %s to the login page', (path) => {
    renderApp(path, false);

    // LoginPage is the only public route; its heading proves the redirect landed.
    expect(
      screen.getByRole('heading', { name: /sign in to promptvault/i }),
    ).toBeInTheDocument();
  });
});

describe('routing — authenticated', () => {
  it('redirects / to the dashboard', async () => {
    renderApp(ROUTES.root, true);

    expect(
      await screen.findByRole('heading', { name: /dashboard/i }),
    ).toBeInTheDocument();
  });

  it('renders a protected route element when authenticated', async () => {
    renderApp(ROUTES.collections, true);

    expect(
      await screen.findByRole('heading', { name: /collections/i }),
    ).toBeInTheDocument();
  });
});
