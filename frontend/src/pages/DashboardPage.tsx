import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getDashboard } from '../api/endpoints';
import { RequestError } from '../api/client';
import { ROUTES } from '../routes';
import { useWorkspace } from '../workspace/WorkspaceContext';
import type { DashboardResponse } from '../api/types';

const CONTENT_PREVIEW_LENGTH = 140;

function preview(content: string): string {
  if (content.length <= CONTENT_PREVIEW_LENGTH) return content;
  return `${content.slice(0, CONTENT_PREVIEW_LENGTH).trimEnd()}…`;
}

function MetricCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-white p-6 shadow">
      <dt className="text-sm font-medium text-gray-500">{label}</dt>
      <dd className="mt-2 text-3xl font-semibold text-gray-900">{value}</dd>
    </div>
  );
}

export function DashboardPage() {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(true);
  // Refetch the aggregate counts whenever the workspace changes (e.g. a
  // collection is created or deleted from the sidebar), so the cards stay live.
  const { revision } = useWorkspace();

  useEffect(() => {
    let active = true;

    setPending(true);
    setError(null);
    getDashboard()
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (!active) return;
        // A 401 is handled globally by the API layer (clears token + redirects),
        // so we only surface non-auth failures here.
        if (err instanceof RequestError) {
          setError(err.body?.error ?? 'Failed to load the dashboard. Please try again.');
        } else {
          setError('Unable to reach the server. Please try again.');
        }
      })
      .finally(() => {
        if (active) setPending(false);
      });

    return () => {
      active = false;
    };
  }, [revision]);

  return (
    <section>
      <h1 className="text-xl font-semibold text-gray-900">Dashboard</h1>

      {pending && !data && <p className="mt-4 text-sm text-gray-500">Loading…</p>}

      {error && (
        <p role="alert" className="mt-4 rounded bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {!error && data && (
        <div className="mt-6 space-y-6">
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard label="Collections" value={data.totalCollections} />
            <MetricCard label="Prompts" value={data.totalPrompts} />
            <MetricCard label="Versions" value={data.totalVersions} />
            <MetricCard label="Latest prompt" value={data.latestPrompt ? 1 : 0} />
          </dl>

          <section aria-labelledby="latest-prompt-heading" className="rounded-lg bg-white p-6 shadow">
            <h2 id="latest-prompt-heading" className="text-xl font-semibold text-gray-900">
              Latest prompt
            </h2>

            {data.latestPrompt ? (
              <Link
                to={ROUTES.prompt(data.latestPrompt.id)}
                className="mt-3 block rounded p-1 hover:bg-gray-50 focus:ring-2 focus:ring-blue-500 focus:outline-none"
              >
                <span className="block font-medium text-blue-600">{data.latestPrompt.title}</span>
                <span className="mt-1 block text-sm text-gray-600">
                  {preview(data.latestPrompt.content)}
                </span>
              </Link>
            ) : (
              <p className="mt-3 text-sm text-gray-500">No prompts yet</p>
            )}
          </section>
        </div>
      )}
    </section>
  );
}
