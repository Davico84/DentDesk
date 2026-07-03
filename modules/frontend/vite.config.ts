import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

// Config base de OdontoRisas (scaffolding). El manejo de SSL/entornos se añadirá
// cuando una capacidad lo requiera.
export default defineConfig({
  plugins: [react()],
  define: {
    // URL del backend; se sobreescribe por entorno. Ver docs/architecture.md §4.
    BACKEND_URL: JSON.stringify(process.env.BACKEND_URL || "http://localhost:8080"),
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
});
