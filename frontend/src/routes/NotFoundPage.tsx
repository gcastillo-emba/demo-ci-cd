import { useEffect } from 'react';
import { Link } from 'react-router';
import { Layout } from '../ui/Layout';

export function NotFoundPage() {
  useEffect(() => {
    document.title = 'Página no encontrada — Asuntos & Horas';
  }, []);

  return (
    <Layout>
      <section className="card" aria-labelledby="notfound-title">
        <h1 id="notfound-title">Página no encontrada</h1>
        <p>
          La dirección no existe. <Link to="/">Volver al inicio</Link>
        </p>
      </section>
    </Layout>
  );
}
