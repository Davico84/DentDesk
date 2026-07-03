import type { ReactElement, ReactNode } from "react";
import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

interface RenderOptions {
  /** Rutas iniciales del MemoryRouter (para flujos con navegación). */
  initialEntries?: string[];
}

/**
 * Renderiza con los providers base (React Query + Router). Crea un QueryClient
 * nuevo por render con reintentos desactivados. Las capacidades añadirán aquí
 * sus providers (sesión, i18n) cuando existan.
 */
export function renderWithProviders(
  ui: ReactElement,
  { initialEntries = ["/"] }: RenderOptions = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={initialEntries}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MemoryRouter>
  );

  return render(ui, { wrapper: Wrapper });
}
