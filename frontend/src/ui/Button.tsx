import type { ButtonHTMLAttributes, Ref } from 'react';

type Variant = 'primary' | 'secondary' | 'outline' | 'danger';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  /** Muestra el estado ocupado y desactiva el botón sin perder el foco. */
  busy?: boolean;
  /** React 19: `ref` llega como prop y se reenvía al `<button>`. */
  ref?: Ref<HTMLButtonElement>;
}

export function Button({
  variant = 'primary',
  busy = false,
  type = 'button',
  className,
  disabled,
  children,
  ...rest
}: ButtonProps) {
  const classes = ['btn', `btn--${variant}`, className].filter(Boolean).join(' ');
  return (
    <button
      {...rest}
      type={type}
      className={classes}
      disabled={disabled}
      aria-disabled={busy || undefined}
      aria-busy={busy || undefined}
      onClick={busy ? (e) => e.preventDefault() : rest.onClick}
    >
      {children}
    </button>
  );
}
