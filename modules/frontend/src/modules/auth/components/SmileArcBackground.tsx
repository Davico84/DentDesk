import { useEffect, useRef } from "react";

/**
 * Arcos ambientales en canvas para el panel de marca del login/registro (arco dental
 * ascendente, en tonos amatista + un acento menta). Decorativo puro (`aria-hidden`); respeta
 * `prefers-reduced-motion` dibujando el cuadro final sin animar.
 */
export function SmileArcBackground() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    let width = 0;
    let height = 0;
    let resizeTimeout: ReturnType<typeof setTimeout>;
    let frameId: number;

    function resize() {
      if (!canvas || !ctx) return;
      const rect = canvas.getBoundingClientRect();
      width = rect.width;
      height = rect.height;
      canvas.width = width * dpr;
      canvas.height = height * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    // Colores de los tokens fijos de producto (update-tailwind-v4, design D5): el canvas no usa
    // clases, así que los lee del CSS; la transparencia animada va en globalAlpha.
    const tokens = getComputedStyle(canvas);
    const arcTeal = tokens.getPropertyValue("--brand-arc-teal").trim();
    const arcLilac = tokens.getPropertyValue("--brand-arc-lilac").trim();

    function drawArcs(progress: number) {
      if (!ctx) return;
      ctx.clearRect(0, 0, width, height);
      const cx = width * 0.5;
      const cy = height * 1.18;
      const base = height * 0.95;
      for (let i = 0; i < 5; i++) {
        const radius = base + i * (height * 0.1);
        const alpha = (0.15 - i * 0.024) * progress;
        if (alpha <= 0) continue;
        ctx.beginPath();
        ctx.strokeStyle = i === 1 ? arcTeal : arcLilac;
        ctx.globalAlpha = i === 1 ? 0.55 * progress : alpha;
        ctx.lineWidth = i === 1 ? 2 : 1.25;
        const span = Math.PI * (0.32 + 0.04 * i);
        ctx.arc(cx, cy, radius, -Math.PI / 2 - span, -Math.PI / 2 + span);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;
    }

    function run() {
      resize();
      if (reduce) {
        drawArcs(1);
        return;
      }
      let start: number | null = null;
      function frame(t: number) {
        if (start === null) start = t;
        const p = Math.min((t - start) / 1100, 1);
        const eased = 1 - Math.pow(1 - p, 3);
        drawArcs(eased);
        if (p < 1) frameId = requestAnimationFrame(frame);
      }
      frameId = requestAnimationFrame(frame);
    }

    run();
    const handleResize = () => {
      clearTimeout(resizeTimeout);
      resizeTimeout = setTimeout(() => {
        resize();
        drawArcs(1);
      }, 150);
    };
    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
      clearTimeout(resizeTimeout);
      cancelAnimationFrame(frameId);
    };
  }, []);

  return (
    <canvas ref={canvasRef} aria-hidden="true" className="absolute inset-0 h-full w-full" />
  );
}
