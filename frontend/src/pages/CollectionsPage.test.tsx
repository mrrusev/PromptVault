import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { WorkspaceProvider } from '../workspace/WorkspaceContext';
import { setToken } from '../api/tokenStore';
import { ROUTES } from '../routes';
import { server } from '../test/server';
import type { Prompt } from '../api/types';
import { CollectionsPage } from './CollectionsPage';

const API = '*/api';

function prompt(id: number, title: string, content = ''): Prompt {
  return { id, title, content, collectionId: 1, ownerId: 1, createdAt: '2026-06-18T10:00:00Z' };
}

// Stub editor route so we can assert that creating a prompt navigates to it.
function PromptEditorStub() {
  const { id } = useParams<{ id: string }>();
  return <div>Editor for prompt {id}</div>;
}

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <WorkspaceProvider>
          <Routes>
            <Route path={ROUTES.collections} element={<CollectionsPage />} />
            <Route path={ROUTES.promptPattern} element={<PromptEditorStub />} />
          </Routes>
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => setToken(null));

describe('CollectionsPage', () => {
  it('lists the selected collection prompts as editor links', async () => {
    server.use(
      http.get(`${API}/prompts`, () =>
        HttpResponse.json([prompt(10, 'First prompt'), prompt(11, 'Second prompt')]),
      ),
    );

    renderPage(ROUTES.collectionSelected(1));

    const first = await screen.findByRole('link', { name: /first prompt/i });
    expect(first).toHaveAttribute('href', ROUTES.prompt(10));

    const second = screen.getByRole('link', { name: /second prompt/i });
    expect(second).toHaveAttribute('href', ROUTES.prompt(11));
  });

  it('shows an empty state for a collection with no prompts', async () => {
    server.use(http.get(`${API}/prompts`, () => HttpResponse.json([])));

    renderPage(ROUTES.collectionSelected(1));

    expect(await screen.findByText(/no prompts yet/i)).toBeInTheDocument();
  });

  it('creates a prompt and navigates to its editor', async () => {
    let postedBody: unknown;
    server.use(
      http.get(`${API}/prompts`, () => HttpResponse.json([])),
      http.post(`${API}/prompts`, async ({ request }) => {
        postedBody = await request.json();
        return HttpResponse.json(prompt(99, 'Draft prompt'));
      }),
    );

    renderPage(ROUTES.collectionSelected(1));

    await screen.findByText(/no prompts yet/i);

    await userEvent.type(screen.getByLabelText(/new prompt title/i), 'Draft prompt');
    await userEvent.click(screen.getByRole('button', { name: /new prompt/i }));

    expect(await screen.findByText(/editor for prompt 99/i)).toBeInTheDocument();
    expect(postedBody).toEqual({ collectionId: 1, title: 'Draft prompt', content: '' });
  });

  it('shows an alert when loading the prompts fails', async () => {
    server.use(
      http.get(`${API}/prompts`, () =>
        HttpResponse.json({ error: 'Server exploded' }, { status: 500 }),
      ),
    );

    renderPage(ROUTES.collectionSelected(1));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Server exploded');
  });

  it('prompts to select a collection when none is selected', () => {
    renderPage(ROUTES.collections);

    expect(screen.getByText(/select a collection, or create one/i)).toBeInTheDocument();
  });
});
