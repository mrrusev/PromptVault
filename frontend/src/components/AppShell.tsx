import { Link, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ROUTES } from '../routes';
import { CollectionsSidebar } from './CollectionsSidebar';

// Shared layout for every protected route: a fixed-width sidebar that stays
// mounted across child-route changes, plus a scrollable main region that owns
// the full-screen chrome and consistent page padding.
export function AppShell() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate(ROUTES.login, { replace: true });
  }

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-64 shrink-0 flex-col border-r border-gray-200 bg-white">
        <div className="border-b border-gray-200 p-4">
          <span className="text-lg font-semibold text-gray-900">PromptVault</span>
        </div>

        <nav className="border-b border-gray-200 p-4">
          <Link
            to={ROUTES.dashboard}
            className="block rounded px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:ring-2 focus:ring-blue-500 focus:outline-none"
          >
            Dashboard
          </Link>
        </nav>

        <CollectionsSidebar />

        <div className="mt-auto border-t border-gray-200 p-4">
          <button
            type="button"
            onClick={handleLogout}
            className="w-full rounded px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:ring-2 focus:ring-blue-500 focus:outline-none"
          >
            Log out
          </button>
        </div>
      </aside>

      <main className="min-h-screen flex-1 overflow-y-auto bg-gray-50 p-6">
        <Outlet />
      </main>
    </div>
  );
}
