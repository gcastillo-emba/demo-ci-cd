import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { ApiProblem, errorMessage } from '../api/problem';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Layout } from '../ui/Layout';
import { TextField } from '../ui/TextField';

interface FieldErrors {
  email?: string;
  password?: string;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateLogin(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  if (email.trim() === '') errors.email = 'Ingresa tu email.';
  else if (!EMAIL_PATTERN.test(email.trim())) errors.email = 'Ingresa un email válido.';
  if (password === '') errors.password = 'Ingresa tu contraseña.';
  return errors;
}

function redirectTarget(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const from = (state as { from: unknown }).from;
    if (typeof from === 'string' && from.startsWith('/') && from !== '/login') return from;
  }
  return '/';
}

export function LoginPage() {
  const { isAuthenticated, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    document.title = 'Iniciar sesión — Asuntos & Horas';
  }, []);

  if (isAuthenticated && !submitting) {
    return <Navigate to={redirectTarget(location.state)} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setFormError(null);

    const errors = validateLogin(email, password);
    setFieldErrors(errors);
    if (errors.email) {
      emailRef.current?.focus();
      return;
    }
    if (errors.password) {
      passwordRef.current?.focus();
      return;
    }

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate(redirectTarget(location.state), { replace: true });
    } catch (error) {
      if (error instanceof ApiProblem) {
        setFieldErrors({
          email: error.fieldError('email'),
          password: error.fieldError('password'),
        });
      }
      setFormError(errorMessage(error));
      setSubmitting(false);
    }
  }

  return (
    <Layout>
      <section className="card login" aria-labelledby="login-title">
        <h1 id="login-title">Iniciar sesión</h1>
        <p className="login__intro">Ingresa con tu cuenta de la firma.</p>
        {formError ? <Alert title="No se pudo iniciar sesión">{formError}</Alert> : null}
        <form onSubmit={handleSubmit} noValidate>
          <TextField
            ref={emailRef}
            label="Email"
            name="email"
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
          />
          <TextField
            ref={passwordRef}
            label="Contraseña"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
          />
          <Button type="submit" busy={submitting} className="login__submit">
            {submitting ? 'Ingresando…' : 'Ingresar'}
          </Button>
        </form>
      </section>
    </Layout>
  );
}
