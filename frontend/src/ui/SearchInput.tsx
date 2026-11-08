import { useEffect, useId, useRef, useState } from 'react';

export const SEARCH_DEBOUNCE_MS = 300;

export interface SearchInputProps {
  label: string;
  /** Valor confirmado (p. ej. el `q` de la URL). */
  value: string;
  /** Se invoca con el texto recortado tras `delay` ms sin escribir. */
  onSearch: (value: string) => void;
  delay?: number;
  hint?: string;
}

/** Campo de búsqueda con _debounce_ (design D9): no consulta la API en cada tecla. */
export function SearchInput({
  label,
  value,
  onSearch,
  delay = SEARCH_DEBOUNCE_MS,
  hint,
}: SearchInputProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const [text, setText] = useState(value);
  const [syncedValue, setSyncedValue] = useState(value);
  const onSearchRef = useRef(onSearch);

  // Si el valor confirmado cambia desde fuera (navegación, alta), el campo lo refleja.
  if (value !== syncedValue) {
    setSyncedValue(value);
    setText(value);
  }

  useEffect(() => {
    onSearchRef.current = onSearch;
  }, [onSearch]);

  useEffect(() => {
    const trimmed = text.trim();
    if (trimmed === value) return undefined;
    const timer = setTimeout(() => onSearchRef.current(trimmed), delay);
    return () => clearTimeout(timer);
  }, [text, value, delay]);

  return (
    <div className="field search">
      <label htmlFor={id} className="field__label">
        {label}
      </label>
      {hint ? (
        <p id={hintId} className="field__hint">
          {hint}
        </p>
      ) : null}
      <input
        id={id}
        type="search"
        className="field__input"
        value={text}
        autoComplete="off"
        aria-describedby={hint ? hintId : undefined}
        onChange={(e) => setText(e.target.value)}
      />
    </div>
  );
}
