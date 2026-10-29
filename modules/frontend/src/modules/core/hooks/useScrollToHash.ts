import { useEffect } from "react";
import { useLocation } from "react-router-dom";

/**
 * Lleva a la vista el elemento `#id` cuando la URL trae ese ancla y el contenido ya está listo. React Router no hace
 * scroll a los anclas por su cuenta, y mientras la sección carga el elemento todavía no existe: por eso `ready`.
 */
export function useScrollToHash(id: string, ready: boolean) {
  const { hash } = useLocation();
  useEffect(() => {
    if (!ready || hash !== `#${id}`) return;
    document.getElementById(id)?.scrollIntoView?.({ block: "start" });
  }, [hash, id, ready]);
}
