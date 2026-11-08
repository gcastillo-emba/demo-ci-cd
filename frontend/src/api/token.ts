// Token de acceso en sessionStorage (ADR-006): se borra al cerrar la pestaña.
export const TOKEN_KEY = 'ah.token';
export const UNAUTHORIZED_EVENT = 'ah:unauthorized';

export function getToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
  } catch {
    // sessionStorage no disponible (modo privado estricto): la sesión vive solo en memoria.
  }
}

export function clearToken(): void {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nada que limpiar.
  }
}
