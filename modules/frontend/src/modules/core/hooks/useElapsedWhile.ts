import { useEffect, useState } from "react";

/**
 * Segundos enteros transcurridos mientras `active` sea verdadero. Vuelve a 0 al pasar a inactivo
 * y limpia su intervalo al desmontar (también en el doble montaje de StrictMode). Hook genérico de
 * `core`: no sabe qué se está esperando.
 */
export function useElapsedWhile(active: boolean): number {
  const [elapsed, setElapsed] = useState(0);
  const [wasActive, setWasActive] = useState(active);

  // Al pasar a inactivo el contador vuelve a 0 durante el render, no en un efecto (sin un pintado
  // intermedio con el valor viejo).
  if (wasActive !== active) {
    setWasActive(active);
    if (!active) setElapsed(0);
  }

  useEffect(() => {
    if (!active) return;
    const start = Date.now();
    const id = setInterval(() => setElapsed(Math.floor((Date.now() - start) / 1000)), 250);
    return () => clearInterval(id);
  }, [active]);

  return active ? elapsed : 0;
}
