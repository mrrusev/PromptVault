import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { useEffect } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { App } from './App';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { WorkspaceProvider } from './workspace/WorkspaceContext';
import { setToken } from './api/tokenStore';
import { ROUTES } from './routes';
import { server } from './test/server';

const API = '*/api';

// The dashboard route fetches GET /dashboard on mount; stub it so the routing
// tests don't trip MSW's unhandled-request guard.
const dashboardStub = {
  totalCollections: 0,
  totalPrompts: 0,
  totalVersions: 0,
  latestPrompt: null,
};

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
      <AuthProvider>
        <WorkspaceProvider>{authenticated ? <SeedAuth /> : <App />}</WorkspaceProvider>
      </AuthProvider>
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
    server.use(
      http.get(`${API}/dashboard`, () => HttpResponse.json(dashboardStub)),
      // The AppShell mounts the collections sidebar on every protected route.
      http.get(`${API}/collections`, () => HttpResponse.json([])),
    );
    renderApp(ROUTES.root, true);

    // The dashboard subtitle is a stable, time-independent signal that the
    // redirect landed on the dashboard (the h1 is a time-of-day greeting).
    expect(
      await screen.findByText(/here's what's in your vault/i),
    ).toBeInTheDocument();
  });

  it('renders a protected route element when authenticated', async () => {
    server.use(http.get(`${API}/collections`, () => HttpResponse.json([])));
    renderApp(ROUTES.collections, true);

    // Level 1 disambiguates the page heading from the sidebar's "Collections" h2.
    expect(
      await screen.findByRole('heading', { level: 1, name: /collections/i }),
    ).toBeInTheDocument();
  });

  it('refreshes the dashboard counts when a collection is created from the sidebar', async () => {
    // The dashboard's collection count tracks server state; creating a
    // collection bumps it, proving the sidebar and dashboard stay in sync
    // without navigating away and back.
    let collectionsCount = 0;
    server.use(
      http.get(`${API}/dashboard`, () =>
        HttpResponse.json({ ...dashboardStub, totalCollections: collectionsCount }),
      ),
      http.get(`${API}/collections`, () => HttpResponse.json([])),
      http.post(`${API}/collections`, async ({ request }) => {
        const body = (await request.json()) as { name: string };
        collectionsCount += 1;
        return HttpResponse.json({
          id: 1,
          name: body.name,
          ownerId: 1,
          createdAt: '2026-06-18T10:00:00Z',
        });
      }),
    );

    renderApp(ROUTES.dashboard, true);

    // Scope assertions to the Collections metric card (the page also has a
    // sidebar "Collections" heading), so this proves the card value, not a
    // stray "0"/"1" elsewhere on the page. The card is the <dt>'s ancestor
    // that also contains the <dd> count.
    await screen.findByText(/here's what's in your vault/i);
    const collectionsCard = (
      await screen.findByText('Collections', { selector: 'dt' })
    ).closest('dl > div') as HTMLElement;
    expect(within(collectionsCard).getByText('0')).toBeInTheDocument();

    // Create a collection via the sidebar's inline form without leaving the page.
    await userEvent.type(screen.getByLabelText(/new collection name/i), 'Research');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    // The Collections card refreshes from 0 to 1 in place.
    expect(await within(collectionsCard).findByText('1')).toBeInTheDocument();
  });
});
