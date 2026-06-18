import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { WorkspaceProvider } from '../workspace/WorkspaceContext';
import { setToken } from '../api/tokenStore';
import { ROUTES } from '../routes';
import { server } from '../test/server';
import type { Prompt, PromptVersion } from '../api/types';
import { PromptEditorPage } from './PromptEditorPage';

const API = '*/api';
const PROMPT_ID = 7;

function prompt(overrides: Partial<Prompt> = {}): Prompt {
  return {
    id: PROMPT_ID,
    title: 'Original title',
    content: 'Original content',
    collectionId: 1,
    ownerId: 1,
    createdAt: '2026-06-18T10:00:00Z',
    ...overrides,
  };
}

function version(versionNumber: number, content: string): PromptVersion {
  return {
    id: versionNumber * 10,
    promptId: PROMPT_ID,
    versionNumber,
    content,
    createdAt: '2026-06-18T10:00:00Z',
  };
}

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={[ROUTES.prompt(PROMPT_ID)]}>
      <AuthProvider>
        <WorkspaceProvider>
          <Routes>
            <Route path={ROUTES.promptPattern} element={<PromptEditorPage />} />
            <Route path={ROUTES.dashboard} element={<div>Dashboard stub</div>} />
          </Routes>
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function loadHandlers(versions: PromptVersion[] = []) {
  return [
    http.get(`${API}/prompts/${PROMPT_ID}`, () => HttpResponse.json(prompt())),
    http.get(`${API}/prompts/${PROMPT_ID}/versions`, () => HttpResponse.json(versions)),
  ];
}

afterEach(() => {
  setToken(null);
  vi.restoreAllMocks();
});

describe('PromptEditorPage', () => {
  it('loads the prompt and its versions', async () => {
    server.use(...loadHandlers([version(1, 'v1'), version(2, 'v2')]));

    renderEditor();

    expect(await screen.findByDisplayValue('Original title')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Original content')).toBeInTheDocument();
    expect(screen.getByText('Version 1')).toBeInTheDocument();
    expect(screen.getByText('Version 2')).toBeInTheDocument();
  });

  it('saves only the changed field and shows a success banner', async () => {
    let patchBody: { title?: string; content?: string } | null = null;
    server.use(
      ...loadHandlers(),
      http.patch(`${API}/prompts/${PROMPT_ID}`, async ({ request }) => {
        patchBody = (await request.json()) as { title?: string; content?: string };
        return HttpResponse.json(prompt({ title: 'New title' }));
      }),
    );

    renderEditor();

    const titleInput = await screen.findByDisplayValue('Original title');
    await userEvent.clear(titleInput);
    await userEvent.type(titleInput, 'New title');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Saved');
    expect(patchBody).toEqual({ title: 'New title' });
    // Re-seeded from the response, so the form is clean again.
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('disables Save version while dirty and re-enables it after saving', async () => {
    server.use(
      ...loadHandlers(),
      http.patch(`${API}/prompts/${PROMPT_ID}`, () =>
        HttpResponse.json(prompt({ title: 'Edited' })),
      ),
    );

    renderEditor();

    const titleInput = await screen.findByDisplayValue('Original title');
    await userEvent.type(titleInput, '!');

    const saveVersionButton = screen.getByRole('button', { name: 'Save version' });
    expect(saveVersionButton).toBeDisabled();
    expect(screen.getByText(/save your changes before snapshotting/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    // After save the form is clean again, so Save version becomes available.
    expect(await screen.findByRole('button', { name: 'Save version' })).toBeEnabled();
    expect(screen.queryByText(/save your changes before snapshotting/i)).not.toBeInTheDocument();
  });

  it('saves a version when clean and appends the new row', async () => {
    server.use(
      ...loadHandlers([version(1, 'v1')]),
      http.post(`${API}/prompts/${PROMPT_ID}/versions`, () =>
        HttpResponse.json(version(2, 'Original content')),
      ),
    );

    renderEditor();

    await screen.findByDisplayValue('Original title');
    await userEvent.click(screen.getByRole('button', { name: 'Save version' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Saved version 2');
    expect(screen.getByText('Version 2')).toBeInTheDocument();
  });

  it('restores a version without confirmation when clean', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm');
    server.use(
      ...loadHandlers([version(1, 'Old snapshot content')]),
      http.post(`${API}/prompts/${PROMPT_ID}/versions/1/restore`, () =>
        HttpResponse.json(prompt({ content: 'Old snapshot content' })),
      ),
    );

    renderEditor();

    await screen.findByDisplayValue('Original content');
    await userEvent.click(screen.getByRole('button', { name: /restore/i }));

    expect(await screen.findByDisplayValue('Old snapshot content')).toBeInTheDocument();
    expect(confirmSpy).not.toHaveBeenCalled();
    // The versions list is untouched by a restore.
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
  });

  it('confirms before restoring while dirty and respects cancel', async () => {
    let restoreCalls = 0;
    server.use(
      ...loadHandlers([version(1, 'Snapshot one')]),
      http.post(`${API}/prompts/${PROMPT_ID}/versions/1/restore`, () => {
        restoreCalls += 1;
        return HttpResponse.json(prompt({ content: 'Snapshot one' }));
      }),
    );

    renderEditor();

    const titleInput = await screen.findByDisplayValue('Original title');
    await userEvent.type(titleInput, ' edited');

    // Cancel: no request, edits preserved.
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValueOnce(false);
    await userEvent.click(screen.getByRole('button', { name: /restore/i }));
    expect(confirmSpy).toHaveBeenCalledOnce();
    expect(restoreCalls).toBe(0);
    expect(screen.getByDisplayValue('Original title edited')).toBeInTheDocument();

    // Confirm: request fires and replaces the live fields.
    confirmSpy.mockReturnValue(true);
    await userEvent.click(screen.getByRole('button', { name: /restore/i }));
    expect(await screen.findByDisplayValue('Snapshot one')).toBeInTheDocument();
    expect(restoreCalls).toBe(1);
  });

  it('deletes the prompt and redirects to the dashboard', async () => {
    server.use(
      ...loadHandlers(),
      http.delete(`${API}/prompts/${PROMPT_ID}`, () => new HttpResponse(null, { status: 204 })),
    );
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    renderEditor();

    await screen.findByDisplayValue('Original title');
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));

    expect(confirmSpy).toHaveBeenCalledOnce();
    expect(await screen.findByText('Dashboard stub')).toBeInTheDocument();
  });

  it('shows a server error and no form when the load fails', async () => {
    server.use(
      http.get(`${API}/prompts/${PROMPT_ID}`, () =>
        HttpResponse.json({ error: 'Server exploded' }, { status: 500 }),
      ),
      http.get(`${API}/prompts/${PROMPT_ID}/versions`, () => HttpResponse.json([])),
    );

    renderEditor();

    expect(await screen.findByRole('alert')).toHaveTextContent('Server exploded');
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
  });

  it('shows a not-found state with a dashboard link on 404', async () => {
    server.use(
      http.get(`${API}/prompts/${PROMPT_ID}`, () =>
        HttpResponse.json({ error: 'Not found' }, { status: 404 }),
      ),
      http.get(`${API}/prompts/${PROMPT_ID}/versions`, () => HttpResponse.json([])),
    );

    renderEditor();

    expect(await screen.findByText(/prompt not found/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to dashboard/i })).toHaveAttribute(
      'href',
      ROUTES.dashboard,
    );
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
  });

  it('shows an empty version state but keeps Save version enabled', async () => {
    server.use(...loadHandlers([]));

    renderEditor();

    await screen.findByDisplayValue('Original title');
    expect(screen.getByText(/no versions yet/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save version' })).toBeEnabled();
  });
});
