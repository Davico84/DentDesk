import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";

// Config base de OdontoRisas (scaffolding). El manejo de SSL se añadirá
// cuando una capacidad lo requiera.
// La URL de la API la resuelve core/config/apiBaseUrl.ts con VITE_API_URL (ver .env.example).
export default defineConfig(() => {
  return {
    // Tailwind 4 como plugin de Vite: sin PostCSS ni autoprefixer (update-tailwind-v4, design D1).
    plugins: [react(), tailwindcss()],
    define: {
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
