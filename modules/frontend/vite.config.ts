import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";

// Config base de OdontoRisas (scaffolding). El manejo de SSL se añadirá
// cuando una capacidad lo requiera.
export default defineConfig(({ mode }) => {
  // Carga variables de .env / .env.<mode> (ver .env.example). Prefijo "" = todas.
  const env = loadEnv(mode, process.cwd(), "");

  return {
    // Tailwind 4 como plugin de Vite: sin PostCSS ni autoprefixer (update-tailwind-v4, design D1).
    plugins: [react(), tailwindcss()],
    define: {
      // URL del backend; se sobreescribe por entorno. Ver docs/architecture.md §4.
      BACKEND_URL: JSON.stringify(env.BACKEND_URL || "http://localhost:8080"),
      // sockjs-client (realtime, futuro) referencia `global`, no definido en el navegador.
      global: "globalThis",
    },
    resolve: {
      alias: { "@": path.resolve(__dirname, "./src") },
    },
    server: {
      port: 5173,
      host: true,
    },
  };
});
