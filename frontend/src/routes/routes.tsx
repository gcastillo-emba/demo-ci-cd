import type { RouteObject } from 'react-router';
import { RequireAuth } from '../auth/RequireAuth';
import { ClientsPage } from './ClientsPage';
import { HomePage } from './HomePage';
import { LoginPage } from './LoginPage';
import { MatterDetailPage } from './MatterDetailPage';
import { MattersPage } from './MattersPage';
import { NotFoundPage } from './NotFoundPage';

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
  { path: '*', element: <NotFoundPage /> },
];
