interface BrandMarkProps {
  className?: string;
}

/**
 * Marca provisional de OdontoRisas: diente estilizado (line-art) con sonrisa y
 * destello, en el color actual (`currentColor`). Se reemplazará por el logo real.
 */
export function BrandMark({ className }: BrandMarkProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" className={className} aria-hidden="true">
      <path
        d="M32 10c-5 0-8 2.5-12 2.5S13 10 13 20c0 8 2.5 14 4.5 22 1.4 5.6 2.8 10 5.5 10 3.4 0 3.4-9 6-9s2.6 9 6 9c2.7 0 4.1-4.4 5.5-10C47.5 34 51 28 51 20c0-10-3-7.5-6-7.5S37 10 32 10Z"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path
        d="M24 31c2.4 2.6 5 4 8 4s5.6-1.4 8-4"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <path
        d="M46 11c.5 3 1.6 4.1 4.6 4.6-3 .5-4.1 1.6-4.6 4.6-.5-3-1.6-4.1-4.6-4.6 3-.5 4.1-1.6 4.6-4.6Z"
        fill="currentColor"
      />
    </svg>
  );
}
