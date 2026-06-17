import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { CollectionsPage } from './pages/CollectionsPage';
import { PromptEditorPage } from './pages/PromptEditorPage';
import { ROUTES } from './routes';

function RootRedirect() {
  const { isAuthenticated } = useAuth();
  return <Navigate to={isAuthenticated ? ROUTES.dashboard : ROUTES.login} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path={ROUTES.root} element={<RootRedirect />} />
      <Route path={ROUTES.login} element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route path={ROUTES.dashboard} element={<DashboardPage />} />
        <Route path={ROUTES.collections} element={<CollectionsPage />} />
        <Route path={ROUTES.promptPattern} element={<PromptEditorPage />} />
      </Route>

      {/* Unknown routes fall back to the root redirect (login or dashboard). */}
      <Route path="*" element={<Navigate to={ROUTES.root} replace />} />
    </Routes>
  );
}
