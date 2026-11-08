import { useEffect } from 'react';
import { roleLabel, useAuth } from '../auth/useAuth';
import { Layout } from '../ui/Layout';

export function HomePage() {
  const { user } = useAuth();

  useEffect(() => {
    document.title = 'Inicio — Asuntos & Horas';
  }, []);

  if (!user) return null;

  return (
    <Layout>
      <section className="card" aria-labelledby="home-title">
        <h1 id="home-title">Bienvenido/a, {user.fullName}</h1>
        <dl className="profile">
          <div>
            <dt>Rol</dt>
            <dd>{roleLabel(user.role)}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{user.email}</dd>
          </div>
        </dl>
      </section>
    </Layout>
  );
}
