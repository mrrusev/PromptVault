import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { createCollection, deleteCollection, getCollections } from '../api/endpoints';
import { RequestError } from '../api/client';
import { ROUTES } from '../routes';
import { useWorkspace } from '../workspace/WorkspaceContext';
import type { Collection } from '../api/types';

const DELETE_CONFIRM_MESSAGE =
  'Delete this collection? All prompts inside it will be permanently removed.';

function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof RequestError) {
    // A 401 is handled globally by the API layer (clears token + redirects),
    // so we only surface non-auth failures here.
    return err.body?.error ?? fallback;
  }
  return 'Unable to reach the server. Please try again.';
}

export function CollectionsSidebar() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const selected = searchParams.get('selected');
  const { notifyChanged } = useWorkspace();

  const [collections, setCollections] = useState<Collection[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [newName, setNewName] = useState('');
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [deletingId, setDeletingId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;

    setLoading(true);
    setError(null);
    getCollections()
      .then((res) => {
        if (active) setCollections(res);
      })
      .catch((err) => {
        if (active) setError(errorMessage(err, 'Failed to load collections. Please try again.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (creating) return;

    const name = newName.trim();
    if (name.length === 0) return;

    setCreating(true);
    setFormError(null);
    try {
      const created = await createCollection({ name });
      setCollections((current) => [...current, created]);
      setNewName('');
      notifyChanged();
    } catch (err) {
      setFormError(errorMessage(err, 'Could not create the collection. Please try again.'));
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(id: number) {
    if (deletingId !== null) return;
    if (!window.confirm(DELETE_CONFIRM_MESSAGE)) return;

    setDeletingId(id);
    setError(null);
    try {
      await deleteCollection(id);
      setCollections((current) => current.filter((c) => c.id !== id));
      notifyChanged();
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the collection. Please try again.'));
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col p-4">
      <h2 className="text-sm font-semibold tracking-wide text-gray-500 uppercase">Collections</h2>

      <form onSubmit={handleCreate} className="mt-3 space-y-2">
        <label htmlFor="new-collection" className="sr-only">
          New collection name
        </label>
        <div className="flex gap-2">
          <input
            id="new-collection"
            name="new-collection"
            type="text"
            placeholder="New collection"
            maxLength={255}
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            className="w-full rounded border border-gray-300 px-2 py-1 text-sm text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:outline-none"
          />
          <button
            type="submit"
            disabled={creating}
            className="rounded bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
          >
            Add
          </button>
        </div>
        {formError && (
          <p role="alert" className="rounded bg-red-50 p-2 text-xs text-red-700">
            {formError}
          </p>
        )}
      </form>

      {error && (
        <p role="alert" className="mt-3 rounded bg-red-50 p-2 text-xs text-red-700">
          {error}
        </p>
      )}

      <div className="mt-3 min-h-0 flex-1 overflow-y-auto">
        {loading && <p className="text-sm text-gray-500">Loading…</p>}

        {!loading && collections.length === 0 && (
          <p className="text-sm text-gray-500">No collections yet</p>
        )}

        {!loading && collections.length > 0 && (
          <ul className="space-y-1">
            {collections.map((collection) => {
              const isActive = selected === String(collection.id);
              return (
                <li key={collection.id} className="flex items-center gap-1">
                  <button
                    type="button"
                    aria-current={isActive ? 'true' : undefined}
                    onClick={() => navigate(ROUTES.collectionSelected(collection.id))}
                    className={`flex-1 truncate rounded px-3 py-2 text-left text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none ${
                      isActive
                        ? 'bg-blue-50 font-medium text-blue-700'
                        : 'text-gray-700 hover:bg-gray-50'
                    }`}
                  >
                    {collection.name}
                  </button>
                  <button
                    type="button"
                    aria-label={`Delete ${collection.name}`}
                    disabled={deletingId === collection.id}
                    onClick={() => handleDelete(collection.id)}
                    className="rounded px-2 py-1 text-sm text-gray-400 hover:bg-red-50 hover:text-red-600 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
                  >
                    ×
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
