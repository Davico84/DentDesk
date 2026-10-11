/**
 * Soporte de Dentdesk (`add-support-contact` D1): cómo escribirle a quien atiende a las clínicas
 * que no pueden entrar. Es el contacto DEL PRODUCTO, no de cada clínica: por eso es una constante y
 * no va en la base.
 *
 * ⚠️ Estos datos quedan públicos en la web (login y registro): cambiarlos es editar este archivo.
 */
export const supportContact = {
  /** Formato de `wa.me`: código de país y número, solo dígitos, sin `+` ni espacios. */
  whatsapp: "51959396384",
  email: "davicova84@gmail.com",
  product: "Dentdesk",
} as const;

/** Enlace de WhatsApp que abre la conversación con un primer mensaje ya escrito. */
export function whatsappUrl(whatsapp: string, text: string): string {
  return `https://wa.me/${whatsapp}?text=${encodeURIComponent(text)}`;
}
