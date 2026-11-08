import { forwardRef, useId, type TextareaHTMLAttributes } from 'react';

export interface TextAreaFieldProps
  extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: string;
  /** Mensaje de error; se asocia al campo con aria-describedby y marca aria-invalid. */
  error?: string;
  hint?: string;
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(
  function TextAreaField({ label, error, hint, required, className, ...rest }, ref) {
    const id = useId();
    const hintId = `${id}-hint`;
    const errorId = `${id}-error`;
    const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

    return (
      <div
        className={['field', error ? 'field--invalid' : null, className].filter(Boolean).join(' ')}
      >
        <label htmlFor={id} className="field__label">
          {label}
          {required ? (
            <span className="field__required" aria-hidden="true">
              {' '}
              *
            </span>
          ) : null}
        </label>
        {hint ? (
          <p id={hintId} className="field__hint">
            {hint}
          </p>
        ) : null}
        <textarea
          {...rest}
          ref={ref}
          id={id}
          className="field__input field__textarea"
          required={required}
          aria-required={required || undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy || undefined}
        />
        {error ? (
          <p id={errorId} className="field__error">
            {error}
          </p>
        ) : null}
      </div>
    );
  },
);
