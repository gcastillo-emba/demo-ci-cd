import type { ReactNode } from 'react';
import { NavLink } from 'react-router';
import { roleLabel, useAuth } from '../auth/useAuth';
import { Button } from './Button';

/** Estructura común: enlace de salto, cabecera con navegación y contenido principal. */
export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();

  return (
    <>
      <a href="#main" className="skip-link">
        Saltar al contenido principal
      </a>
      <header className="app-header">
        <div className="app-header__inner">
          <span className="app-header__brand">Asuntos &amp; Horas</span>
          {user ? (
            <>
              <nav aria-label="Principal" className="app-nav">
                <ul>
                  <li>
                    <NavLink to="/" end>
                      Inicio
                    </NavLink>
                  </li>
                  <li>
                    <NavLink to="/clientes">Clientes</NavLink>
                  </li>
                  <li>
                    <NavLink to="/asuntos">Asuntos</NavLink>
                  </li>
                </ul>
              </nav>
              <div className="app-header__user">
                <span>
                  {user.fullName} <span className="app-header__role">({roleLabel(user.role)})</span>
                </span>
                <Button variant="secondary" onClick={logout}>
                  Cerrar sesión
                </Button>
              </div>
            </>
          ) : null}
        </div>
      </header>
      <main id="main" tabIndex={-1} className="app-main">
        {children}
      </main>
    </>
  );
}
