import { useEffect, useState } from "react";

/**
 * Retrasa un valor hasta que deja de cambiar durante `delay` ms. Primitivo genérico de `core`.
 *
 * <p>Su uso típico es un buscador: sin esto, cada tecla dispara una petición (escribir "Ada
 * Lovelace" serían 12 llamadas al servidor, y las respuestas pueden llegar desordenadas). Con el
 * retraso, solo se consulta cuando la persona **deja de escribir**.
 *
 * <p>El input sigue siendo inmediato: lo que se retrasa es la **consulta**, no lo que se teclea.
 */
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}
