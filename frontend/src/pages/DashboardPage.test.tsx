import { render, screen } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { WorkspaceProvider } from '../workspace/WorkspaceContext';
import { setToken } from '../api/tokenStore';
import { ROUTES } from '../routes';
import { server } from '../test/server';
import { DashboardPage } from './DashboardPage';

const API = '*/api';

function renderDashboard() {
  return render(
    <MemoryRouter initialEntries={[ROUTES.dashboard]}>
      <AuthProvider>
        <WorkspaceProvider>
          <DashboardPage />
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => setToken(null));

describe('DashboardPage', () => {
  it('renders the three stat cards and the recent prompt on success', async () => {
    server.use(
      http.get(`${API}/dashboard`, () =>
        HttpResponse.json({
          totalCollections: 2,
          totalPrompts: 3,
          totalVersions: 5,
          latestPrompt: {
            id: 42,
            title: 'My latest prompt',
            content: 'Generate a haiku about reactive streams.',
            collectionId: 1,
            ownerId: 1,
            createdAt: '2026-06-18T10:00:00Z',
          },
        }),
      ),
    );

    renderDashboard();

    // Counts appear once the fetch resolves.
    expect(await screen.findByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();

    // Labels for the three stat cards.
    expect(screen.getByText(/^collections$/i)).toBeInTheDocument();
    expect(screen.getByText(/^prompts$/i)).toBeInTheDocument();
    expect(screen.getByText(/^versions$/i)).toBeInTheDocument();

    // Recent prompt title renders and the "Open editor" action links to the editor route.
    expect(screen.getByRole('heading', { name: /my latest prompt/i })).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /open editor/i });
    expect(link).toHaveAttribute('href', ROUTES.prompt(42));
    expect(screen.getByText(/generate a haiku about reactive streams/i)).toBeInTheDocument();
  });

  it('shows 0/0/0 and an empty state when there is no latest prompt', async () => {
    server.use(
      http.get(`${API}/dashboard`, () =>
        HttpResponse.json({
          totalCollections: 0,
          totalPrompts: 0,
          totalVersions: 0,
          latestPrompt: null,
        }),
      ),
    );

    renderDashboard();

    // The three stat cards all show 0.
    expect(await screen.findAllByText('0')).toHaveLength(3);
    expect(screen.getByText(/no prompts yet/i)).toBeInTheDocument();
    // The friendly empty state offers a CTA to collections, but no prompt-editor link.
    expect(screen.queryByRole('link', { name: /open editor/i })).not.toBeInTheDocument();
  });

  it('shows an error via role="alert" when the request fails', async () => {
    server.use(
      http.get(`${API}/dashboard`, () =>
        HttpResponse.json({ error: 'Server exploded' }, { status: 500 }),
      ),
    );

    renderDashboard();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Server exploded');
  });
});
