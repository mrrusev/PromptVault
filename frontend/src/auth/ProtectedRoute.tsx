import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { ROUTES } from '../routes';

// Gate for every non-public route: redirect to /login when there is no token.
export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to={ROUTES.login} replace />;
  }

  return <Outlet />;
}
