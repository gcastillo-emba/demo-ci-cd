import type { ReactNode } from 'react';

export interface AlertProps {
  title?: string;
  variant?: 'error' | 'info';
  children: ReactNode;
}

/** Mensaje anunciado por lectores de pantalla (role="alert"). */
export function Alert({ title, variant = 'error', children }: AlertProps) {
  return (
    <div role="alert" className={`alert alert--${variant}`}>
      {title ? <p className="alert__title">{title}</p> : null}
      <p className="alert__body">{children}</p>
    </div>
  );
}
