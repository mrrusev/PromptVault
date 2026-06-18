import type { PromptVersion } from '../api/types';

interface VersionHistoryPanelProps {
  // Stored ascending (as the API returns them); displayed newest-first.
  versions: PromptVersion[];
  restoringVersion: number | null;
  onRestore: (versionNumber: number) => void;
}

function formatCreatedAt(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString();
}

// Presentational only — no data fetching. The parent owns the versions list,
// the in-flight restore state, and the restore handler.
export function VersionHistoryPanel({
  versions,
  restoringVersion,
  onRestore,
}: VersionHistoryPanelProps) {
  if (versions.length === 0) {
    return <p className="mt-3 text-sm text-gray-500">No versions yet</p>;
  }

  // Newest-first for display; copy first so we never mutate the parent's list.
  const ordered = [...versions].reverse();

  return (
    <ul className="mt-3 space-y-2">
      {ordered.map((version) => (
        <li
          key={version.id}
          className="flex items-center justify-between gap-2 rounded border border-gray-200 p-2"
        >
          <div className="min-w-0">
            <span className="block text-sm font-medium text-gray-900">
              Version {version.versionNumber}
            </span>
            <span className="block text-xs text-gray-500">{formatCreatedAt(version.createdAt)}</span>
          </div>
          <button
            type="button"
            disabled={restoringVersion === version.versionNumber}
            onClick={() => onRestore(version.versionNumber)}
            className="rounded px-3 py-1 text-sm font-medium text-blue-600 hover:bg-blue-50 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
          >
            {restoringVersion === version.versionNumber ? 'Restoring…' : 'Restore'}
          </button>
        </li>
      ))}
    </ul>
  );
}
