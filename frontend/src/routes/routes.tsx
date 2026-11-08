import type { RouteObject } from 'react-router';
import type { Role } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';
import { RequireRole } from '../auth/RequireRole';
import { ApprovalsPage } from './ApprovalsPage';
import { ClientsPage } from './ClientsPage';
import { HomePage } from './HomePage';
import { LoginPage } from './LoginPage';
import { MatterDetailPage } from './MatterDetailPage';
import { MattersPage } from './MattersPage';
import { MyTimeEntriesPage } from './MyTimeEntriesPage';
import { NotFoundPage } from './NotFoundPage';
import { RegisterHoursPage } from './RegisterHoursPage';

const LAWYER_ONLY: readonly Role[] = ['LAWYER'];
const PARTNER_ONLY: readonly Role[] = ['PARTNER'];

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <HomePage />
      </RequireAuth>
    ),
  },
  {
    path: '/clientes',
    element: (
      <RequireAuth>
        <ClientsPage />
      </RequireAuth>
    ),
  },
  {
    path: '/asuntos',
    element: (
      <RequireAuth>
        <MattersPage />
      </RequireAuth>
    ),
  },
  {
    path: '/asuntos/:matterId',
    element: (
      <RequireAuth>
        <MatterDetailPage />
      </RequireAuth>
    ),
  },
  // C2 — guard por rol con el `role` de GET /api/me (design D8).
  {
    path: '/registrar-horas',
    element: (
      <RequireAuth>
        <RequireRole roles={LAWYER_ONLY}>
          <RegisterHoursPage />
        </RequireRole>
      </RequireAuth>
    ),
  },
  {
    path: '/mis-registros',
    element: (
      <RequireAuth>
        <RequireRole roles={LAWYER_ONLY}>
          <MyTimeEntriesPage />
        </RequireRole>
      </RequireAuth>
    ),
  },
  {
    path: '/aprobaciones',
    element: (
      <RequireAuth>
        <RequireRole roles={PARTNER_ONLY}>
          <ApprovalsPage />
        </RequireRole>
      </RequireAuth>
    ),
  },
  { path: '*', element: <NotFoundPage /> },
];
