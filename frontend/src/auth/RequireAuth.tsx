import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { errorMessage } from '../api/problem';
import { Alert } from '../ui/Alert';
import { useAuth } from './useAuth';

/** Protege una ruta: sin sesión redirige a /login recordando el destino. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, user, isLoadingUser, userError } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (isLoadingUser) {
    return (
      <p role="status" className="status">
        Cargando tu sesión…
      </p>
    );
  }
  if (!user) {
    return <Alert>{errorMessage(userError)}</Alert>;
  }
  return <>{children}</>;
}
