import { useEffect, type ReactNode } from 'react';
import { Alert } from '../ui/Alert';
import { Layout } from '../ui/Layout';
import type { Role } from './AuthContext';
import { useAuth } from './useAuth';

export const ROLE_DENIED_MESSAGE = 'Tu rol no permite acceder a esta pantalla.';

/**
 * Guard por rol (design D8) con el `role` de `GET /api/me`. Va dentro de `RequireAuth`, así que el
 * usuario ya está cargado. Es solo presentación: la API aplica la autorización de todos modos.
 */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const { user } = useAuth();
  const allowed = user !== undefined && roles.includes(user.role);

  useEffect(() => {
    if (!allowed) document.title = 'Acceso no permitido — Asuntos & Horas';
  }, [allowed]);

  if (allowed) return <>{children}</>;
  return (
    <Layout>
      <section className="card" aria-labelledby="denied-title">
        <h1 id="denied-title">Acceso no permitido</h1>
        <Alert>{ROLE_DENIED_MESSAGE}</Alert>
      </section>
    </Layout>
  );
}
