import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { WorkspaceProvider } from '../workspace/WorkspaceContext';
import { setToken } from '../api/tokenStore';
import { ROUTES } from '../routes';
import { server } from '../test/server';
import type { Collection } from '../api/types';
import { CollectionsSidebar } from './CollectionsSidebar';

const API = '*/api';

function collection(id: number, name: string): Collection {
  return { id, name, ownerId: 1, createdAt: '2026-06-18T10:00:00Z' };
}

function renderSidebar() {
  return render(
    <MemoryRouter initialEntries={[ROUTES.collections]}>
      <AuthProvider>
        <WorkspaceProvider>
          <CollectionsSidebar />
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  setToken(null);
  vi.restoreAllMocks();
});

describe('CollectionsSidebar', () => {
  it('renders the fetched collections list', async () => {
    server.use(
      http.get(`${API}/collections`, () =>
        HttpResponse.json([collection(1, 'Coding'), collection(2, 'Writing')]),
      ),
    );

    renderSidebar();

    expect(await screen.findByRole('button', { name: 'Coding' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Writing' })).toBeInTheDocument();
  });

  it('creates a collection via the inline form and shows it in the list', async () => {
    server.use(
      http.get(`${API}/collections`, () => HttpResponse.json([])),
      http.post(`${API}/collections`, async ({ request }) => {
        const body = (await request.json()) as { name: string };
        return HttpResponse.json(collection(7, body.name));
      }),
    );

    renderSidebar();

    expect(await screen.findByText(/no collections yet/i)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/new collection name/i), 'Research');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('button', { name: 'Research' })).toBeInTheDocument();
  });

  it('asks for confirmation and removes the row on delete', async () => {
    server.use(
      http.get(`${API}/collections`, () => HttpResponse.json([collection(1, 'Coding')])),
      http.delete(`${API}/collections/1`, () => new HttpResponse(null, { status: 204 })),
    );

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    renderSidebar();

    const row = (await screen.findByRole('button', { name: 'Coding' })).closest('li');
    expect(row).not.toBeNull();

    await userEvent.click(within(row as HTMLElement).getByRole('button', { name: /delete coding/i }));

    expect(confirmSpy).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: 'Coding' })).not.toBeInTheDocument();
  });

  it('surfaces the server error message when create fails', async () => {
    server.use(
      http.get(`${API}/collections`, () => HttpResponse.json([])),
      http.post(`${API}/collections`, () =>
        HttpResponse.json({ error: 'Name must be 1-255 characters' }, { status: 400 }),
      ),
    );

    renderSidebar();

    await screen.findByText(/no collections yet/i);

    await userEvent.type(screen.getByLabelText(/new collection name/i), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Name must be 1-255 characters');
  });
});
