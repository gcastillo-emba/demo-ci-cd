import type { RouteObject } from 'react-router';
import { RequireAuth } from '../auth/RequireAuth';
import { HomePage } from './HomePage';
import { LoginPage } from './LoginPage';
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
  { path: '*', element: <NotFoundPage /> },
];
