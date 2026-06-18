import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { getDashboard } from '../api/endpoints';
import { RequestError } from '../api/client';
import { ROUTES } from '../routes';
import { useAuth } from '../auth/AuthContext';
import { useWorkspace } from '../workspace/WorkspaceContext';
import type { DashboardResponse } from '../api/types';

const CONTENT_PREVIEW_LENGTH = 140;

function preview(content: string): string {
  if (content.length <= CONTENT_PREVIEW_LENGTH) return content;
  return `${content.slice(0, CONTENT_PREVIEW_LENGTH).trimEnd()}…`;
}

// Time-of-day greeting computed client-side; no locale/timezone deps needed.
function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

// Lightweight relative-time helper — avoids pulling in date-fns/dayjs.
function relativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const diff = Date.now() - then;
  if (diff < MINUTE) return 'just now';
  if (diff < HOUR) {
    const minutes = Math.floor(diff / MINUTE);
    return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;
  }
  if (diff < DAY) {
    const hours = Math.floor(diff / HOUR);
    return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  }
  const days = Math.floor(diff / DAY);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

type Accent = 'indigo' | 'sky' | 'amber';

const ACCENT_STYLES: Record<Accent, { ring: string; iconBg: string; iconText: string }> = {
  indigo: { ring: 'ring-indigo-100', iconBg: 'bg-indigo-50', iconText: 'text-indigo-600' },
  sky: { ring: 'ring-sky-100', iconBg: 'bg-sky-50', iconText: 'text-sky-600' },
  amber: { ring: 'ring-amber-100', iconBg: 'bg-amber-50', iconText: 'text-amber-600' },
};

function FolderIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true" className="h-5 w-5">
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
    </svg>
  );
}

function DocumentIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true" className="h-5 w-5">
      <path strokeLinecap="round" strokeLinejoin="round" d="M7 3h7l5 5v13a0 0 0 0 1 0 0H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M14 3v5h5M9 13h6M9 17h6" />
    </svg>
  );
}

function ClockHistoryIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true" className="h-5 w-5">
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 12a9 9 0 1 0 3-6.7M3 4v3h3" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 2" />
    </svg>
  );
}

function StatCard({
  label,
  value,
  accent,
  icon,
}: {
  label: string;
  value: number;
  accent: Accent;
  icon: ReactNode;
}) {
  const style = ACCENT_STYLES[accent];
  return (
    <div className={`rounded-xl border border-gray-200 bg-white p-5 shadow-sm ring-1 ${style.ring} transition-shadow hover:shadow-md`}>
      <div className="flex items-center justify-between">
        <dt className="text-sm font-medium text-gray-500">{label}</dt>
        <span className={`flex h-9 w-9 items-center justify-center rounded-full ${style.iconBg} ${style.iconText}`}>
          {icon}
        </span>
      </div>
      <dd className="mt-3 text-3xl font-bold tracking-tight text-gray-900">{value}</dd>
    </div>
  );
}

export function DashboardPage() {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(true);
  const { user } = useAuth();
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

  const latest = data?.latestPrompt ?? null;

  return (
    <section>
      <header className="border-b border-gray-200 pb-5">
        <h1 className="text-2xl font-bold tracking-tight text-gray-900">
          {greeting()}
          {user?.username ? `, ${user.username}` : ''}
        </h1>
        <p className="mt-1 text-sm text-gray-500">Here's what's in your vault</p>
      </header>

      {pending && !data && <p className="mt-6 text-sm text-gray-500">Loading…</p>}

      {error && (
        <p role="alert" className="mt-6 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {!error && data && (
        <div className="mt-6 space-y-6">
          <dl className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <StatCard label="Collections" value={data.totalCollections} accent="indigo" icon={<FolderIcon />} />
            <StatCard label="Prompts" value={data.totalPrompts} accent="sky" icon={<DocumentIcon />} />
            <StatCard label="Versions" value={data.totalVersions} accent="amber" icon={<ClockHistoryIcon />} />
          </dl>

          <section
            aria-labelledby="recent-prompt-heading"
            className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm"
          >
            <div className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-50 text-indigo-600">
                <DocumentIcon />
              </span>
              <h2 id="recent-prompt-heading" className="text-base font-semibold text-gray-900">
                Recent prompt
              </h2>
            </div>

            {latest ? (
              <div className="mt-4">
                <h3 className="text-lg font-semibold text-gray-900">{latest.title}</h3>
                <p className="mt-2 line-clamp-2 text-sm text-gray-600">{preview(latest.content)}</p>
                <div className="mt-4 flex items-center justify-between">
                  <span className="text-xs text-gray-400">Updated {relativeTime(latest.createdAt)}</span>
                  <Link
                    to={ROUTES.prompt(latest.id)}
                    className="inline-flex items-center gap-1 rounded-md text-sm font-medium text-indigo-600 hover:text-indigo-700 focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  >
                    Open editor
                    <span aria-hidden="true">→</span>
                  </Link>
                </div>
              </div>
            ) : (
              <div className="mt-4 rounded-lg border border-dashed border-gray-200 bg-gray-50 p-8 text-center">
                <p className="text-sm font-medium text-gray-900">No prompts yet</p>
                <p className="mt-1 text-sm text-gray-500">
                  Create a collection from the sidebar, then add your first prompt.
                </p>
                <Link
                  to={ROUTES.collections}
                  className="mt-4 inline-flex items-center gap-1 rounded-md text-sm font-medium text-indigo-600 hover:text-indigo-700 focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                >
                  Go to collections
                  <span aria-hidden="true">→</span>
                </Link>
              </div>
            )}
          </section>
        </div>
      )}
    </section>
  );
}
