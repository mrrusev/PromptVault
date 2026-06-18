import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { createPrompt, getPrompts } from '../api/endpoints';
import { RequestError } from '../api/client';
import { ROUTES } from '../routes';
import { useWorkspace } from '../workspace/WorkspaceContext';
import type { Prompt } from '../api/types';

const CONTENT_PREVIEW_LENGTH = 140;

function preview(content: string): string {
  if (content.length <= CONTENT_PREVIEW_LENGTH) return content;
  return `${content.slice(0, CONTENT_PREVIEW_LENGTH).trimEnd()}…`;
}

function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof RequestError) {
    // A 401 is handled globally by the API layer (clears token + redirects),
    // so we only surface non-auth failures here.
    return err.body?.error ?? fallback;
  }
  return 'Unable to reach the server. Please try again.';
}

// The selected-collection view: lists the collection's prompts and lets the
// user create a new one (landing them in the editor to write its content).
// Keyed by `collectionId` so the load effect refetches when the selection
// changes; extracting it keeps that dependency a stable number.
function SelectedCollection({ collectionId }: { collectionId: number }) {
  const navigate = useNavigate();
  const { notifyChanged } = useWorkspace();

  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [newTitle, setNewTitle] = useState('');
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    setLoading(true);
    setError(null);
    getPrompts(collectionId)
      .then((res) => {
        if (active) setPrompts(res);
      })
      .catch((err) => {
        if (active) setError(errorMessage(err, 'Failed to load prompts. Please try again.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [collectionId]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (creating) return;

    const title = newTitle.trim();
    if (title.length === 0) return;

    setCreating(true);
    setFormError(null);
    try {
      const created = await createPrompt({ collectionId, title, content: '' });
      setNewTitle('');
      // A new prompt changes the dashboard's aggregate counts.
      notifyChanged();
      navigate(ROUTES.prompt(created.id));
    } catch (err) {
      setFormError(errorMessage(err, 'Could not create the prompt. Please try again.'));
    } finally {
      setCreating(false);
    }
  }

  return (
    <section>
      <h1 className="text-xl font-semibold text-gray-900">Collection #{collectionId}</h1>

      <form onSubmit={handleCreate} className="mt-4 flex gap-2">
        <label htmlFor="new-prompt" className="sr-only">
          New prompt title
        </label>
        <input
          id="new-prompt"
          name="new-prompt"
          type="text"
          placeholder="New prompt"
          maxLength={255}
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          className="w-full max-w-md rounded border border-gray-300 px-2 py-1 text-sm text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={creating}
          className="rounded bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
        >
          New prompt
        </button>
      </form>

      {formError && (
        <p role="alert" className="mt-3 rounded bg-red-50 p-2 text-sm text-red-700">
          {formError}
        </p>
      )}

      {loading && <p className="mt-4 text-sm text-gray-500">Loading…</p>}

      {error && (
        <p role="alert" className="mt-4 rounded bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {!loading && !error && prompts.length === 0 && (
        <p className="mt-4 text-sm text-gray-500">No prompts yet</p>
      )}

      {!loading && !error && prompts.length > 0 && (
        <ul className="mt-6 space-y-3">
          {prompts.map((prompt) => (
            <li key={prompt.id} className="rounded-lg bg-white p-6 shadow">
              <Link
                to={ROUTES.prompt(prompt.id)}
                className="block rounded p-1 hover:bg-gray-50 focus:ring-2 focus:ring-blue-500 focus:outline-none"
              >
                <span className="block font-medium text-blue-600">{prompt.title}</span>
                {prompt.content && (
                  <span className="mt-1 block text-sm text-gray-600">{preview(prompt.content)}</span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// The sidebar (mounted by AppShell) owns the collections list and its actions.
// This page reflects the currently selected collection from the URL: its prompts
// and a "New prompt" action that opens the editor.
export function CollectionsPage() {
  const [searchParams] = useSearchParams();
  const selected = searchParams.get('selected');

  if (!selected) {
    return (
      <section>
        <h1 className="text-xl font-semibold text-gray-900">Collections</h1>
        <p className="mt-4 text-sm text-gray-500">Select a collection, or create one</p>
      </section>
    );
  }

  return <SelectedCollection collectionId={Number(selected)} />;
}
