import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  deletePrompt,
  getPrompt,
  getVersions,
  restoreVersion,
  saveVersion,
  updatePrompt,
} from '../api/endpoints';
import { RequestError } from '../api/client';
import { ROUTES } from '../routes';
import { useWorkspace } from '../workspace/WorkspaceContext';
import type { Prompt, PromptVersion, UpdatePromptRequest } from '../api/types';
import { VersionHistoryPanel } from './VersionHistoryPanel';

const HTTP_NOT_FOUND = 404;

const DELETE_CONFIRM_MESSAGE = 'Delete this prompt? This cannot be undone.';
const RESTORE_CONFIRM_MESSAGE = 'Discard your unsaved changes and restore this version?';
const SAVE_VERSION_HINT = 'Save your changes before snapshotting a version.';

function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof RequestError) {
    // A 401 is handled globally by the API layer (clears token + redirects),
    // so we only surface non-auth failures here.
    return err.body?.error ?? fallback;
  }
  return 'Unable to reach the server. Please try again.';
}

const INPUT_CLASSES =
  'w-full rounded border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:outline-none';

export function PromptEditorPage() {
  const { id } = useParams<{ id: string }>();
  const promptId = Number(id);
  const navigate = useNavigate();
  const { notifyChanged } = useWorkspace();

  // Server-known truth: the live prompt as the backend last returned it. Editing
  // `title`/`content` away from this is what makes the form "dirty". Re-seeding
  // both from each server response clears the dirty flag automatically.
  const [prompt, setPrompt] = useState<Prompt | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [versions, setVersions] = useState<PromptVersion[]>([]);

  const [pending, setPending] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savingVersion, setSavingVersion] = useState(false);
  const [restoringVersion, setRestoringVersion] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  const dirty =
    prompt !== null && (title !== prompt.title || content !== prompt.content);

  function seed(next: Prompt) {
    setPrompt(next);
    setTitle(next.title);
    setContent(next.content);
  }

  useEffect(() => {
    // A non-numeric id can never match a backend prompt; treat as not-found.
    if (Number.isNaN(promptId)) {
      setNotFound(true);
      setPending(false);
      return;
    }

    let active = true;

    setPending(true);
    setError(null);
    setNotFound(false);
    Promise.all([getPrompt(promptId), getVersions(promptId)])
      .then(([loadedPrompt, loadedVersions]) => {
        if (!active) return;
        seed(loadedPrompt);
        setVersions(loadedVersions);
      })
      .catch((err) => {
        if (!active) return;
        if (err instanceof RequestError && err.status === HTTP_NOT_FOUND) {
          setNotFound(true);
          return;
        }
        setError(errorMessage(err, 'Failed to load the prompt. Please try again.'));
      })
      .finally(() => {
        if (active) setPending(false);
      });

    return () => {
      active = false;
    };
  }, [promptId]);

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving || !dirty || prompt === null) return;

    setError(null);
    setSuccess(null);

    const patch: UpdatePromptRequest = {};
    if (title !== prompt.title) patch.title = title;
    if (content !== prompt.content) patch.content = content;
    if (Object.keys(patch).length === 0) return;

    setSaving(true);
    try {
      const updated = await updatePrompt(promptId, patch);
      seed(updated);
      setSuccess('Saved');
    } catch (err) {
      setError(errorMessage(err, 'Could not save the prompt. Please try again.'));
    } finally {
      setSaving(false);
    }
  }

  async function handleSaveVersion() {
    if (savingVersion || saving || dirty) return;

    setError(null);
    setSuccess(null);
    setSavingVersion(true);
    try {
      const created = await saveVersion(promptId);
      // Keep storage ascending; the panel reverses for display.
      setVersions((current) => [...current, created]);
      setSuccess(`Saved version ${created.versionNumber}`);
      notifyChanged();
    } catch (err) {
      setError(errorMessage(err, 'Could not save a version. Please try again.'));
    } finally {
      setSavingVersion(false);
    }
  }

  async function handleRestore(versionNumber: number) {
    if (restoringVersion !== null) return;
    if (dirty && !window.confirm(RESTORE_CONFIRM_MESSAGE)) return;

    setError(null);
    setSuccess(null);
    setRestoringVersion(versionNumber);
    try {
      const restored = await restoreVersion(promptId, versionNumber);
      seed(restored);
      setSuccess(`Restored version ${versionNumber}`);
    } catch (err) {
      setError(errorMessage(err, 'Could not restore the version. Please try again.'));
    } finally {
      setRestoringVersion(null);
    }
  }

  async function handleDelete() {
    if (deleting) return;
    if (!window.confirm(DELETE_CONFIRM_MESSAGE)) return;

    setError(null);
    setSuccess(null);
    setDeleting(true);
    try {
      await deletePrompt(promptId);
      notifyChanged();
      navigate(ROUTES.dashboard, { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the prompt. Please try again.'));
      setDeleting(false);
    }
  }

  if (pending) {
    return (
      <section>
        <h1 className="text-xl font-semibold text-gray-900">Edit prompt</h1>
        <p className="mt-4 text-sm text-gray-500">Loading…</p>
      </section>
    );
  }

  if (notFound) {
    return (
      <section>
        <h1 className="text-xl font-semibold text-gray-900">Prompt not found</h1>
        <Link
          to={ROUTES.dashboard}
          className="mt-4 inline-block text-sm font-medium text-blue-600 hover:text-blue-700 focus:ring-2 focus:ring-blue-500 focus:outline-none"
        >
          Back to dashboard
        </Link>
      </section>
    );
  }

  if (error && prompt === null) {
    return (
      <section>
        <h1 className="text-xl font-semibold text-gray-900">Edit prompt</h1>
        <p role="alert" className="mt-4 rounded bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      </section>
    );
  }

  return (
    <section>
      <h1 className="text-xl font-semibold text-gray-900">Edit prompt</h1>

      {error && (
        <p role="alert" className="mt-4 rounded bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {success && (
        <p role="status" className="mt-4 rounded bg-green-50 p-2 text-sm text-green-700">
          {success}
        </p>
      )}

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <form onSubmit={handleSave} className="rounded-lg bg-white p-6 shadow lg:col-span-2">
          <div className="space-y-1">
            <label htmlFor="prompt-title" className="block text-sm font-medium text-gray-700">
              Title
            </label>
            <input
              id="prompt-title"
              name="title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className={INPUT_CLASSES}
            />
          </div>

          <div className="mt-4 space-y-1">
            <label htmlFor="prompt-content" className="block text-sm font-medium text-gray-700">
              Content
            </label>
            <textarea
              id="prompt-content"
              name="content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              className={`${INPUT_CLASSES} min-h-64 font-mono`}
            />
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              type="submit"
              disabled={!dirty || saving}
              className="rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
            >
              {saving ? 'Saving…' : 'Save'}
            </button>
            <button
              type="button"
              onClick={handleSaveVersion}
              disabled={dirty || savingVersion || saving}
              className="rounded border border-gray-300 px-4 py-2 font-medium text-gray-700 hover:bg-gray-50 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
            >
              {savingVersion ? 'Saving version…' : 'Save version'}
            </button>
            <button
              type="button"
              onClick={handleDelete}
              disabled={deleting}
              className="rounded px-4 py-2 font-medium text-red-600 hover:bg-red-50 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
            >
              {deleting ? 'Deleting…' : 'Delete'}
            </button>
          </div>

          {dirty && <p className="mt-2 text-xs text-gray-500">{SAVE_VERSION_HINT}</p>}
        </form>

        <aside className="rounded-lg bg-white p-6 shadow">
          <h2 className="text-xl font-semibold text-gray-900">Version history</h2>
          <VersionHistoryPanel
            versions={versions}
            restoringVersion={restoringVersion}
            onRestore={handleRestore}
          />
        </aside>
      </div>
    </section>
  );
}
