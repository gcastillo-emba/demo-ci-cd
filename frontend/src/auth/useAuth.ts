import { useContext } from 'react';
import { AuthContext, type AuthState, type Role } from './AuthContext';

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return ctx;
}

const ROLE_LABELS: Record<Role, string> = {
  PARTNER: 'Socio',
  LAWYER: 'Abogado',
};

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role];
}
