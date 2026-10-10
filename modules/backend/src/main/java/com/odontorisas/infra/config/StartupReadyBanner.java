package com.odontorisas.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aviso destacado en el log cuando el backend ya atiende peticiones ({@link ApplicationReadyEvent}):
 * nombre del producto, URL con el puerto real y Swagger UI. Es la señal positiva de que el arranque
 * pasó entero, incluida la validación del secreto JWT (update-jwt-secret-validation, design D5).
 *
 * <p>Lee SOLO cuatro propiedades no sensibles, así no puede filtrar secretos por construcción. Solo
 * ASCII: la consola de Windows no usa UTF-8 por defecto. Sin servidor web (p. ej. tests con
 * MockMvc) no hay {@code local.server.port} y no escribe nada. Portado de HSO.
 */
@Component
class StartupReadyBanner {

    private static final Logger log = LoggerFactory.getLogger(StartupReadyBanner.class);
    private static final String RULE = "=".repeat(60);
    /** Nombre del producto: es texto visible, así que va con el nombre comercial (CLAUDE.md). */
    private static final String DEFAULT_NAME = "Dentdesk";

    private final Environment environment;

    StartupReadyBanner(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        Integer port = environment.getProperty("local.server.port", Integer.class);
        if (port == null) {
            return;
        }
        List<String> lines = render(environment.getProperty("app.name", DEFAULT_NAME),
            port,
            environment.getProperty("server.servlet.context-path", ""),
            swaggerEnabled());
        // Una sola entrada de log que empieza con salto de línea: el prefijo (fecha, hilo, clase)
        // sale una vez y el bloque queda limpio y alineado debajo.
        log.info("{}{}", System.lineSeparator(), String.join(System.lineSeparator(), lines));
    }

    /**
     * Swagger UI sirve de algo solo si también existe el documento que carga: con
     * {@code springdoc.swagger-ui.enabled=true} y {@code springdoc.api-docs.enabled=false} (un
     * override directo de springdoc, saltándose {@code SWAGGER_ENABLED}) la UI abre vacía, así
     * que el aviso la da por desactivada. Por defecto, ambas apagadas.
     */
    private boolean swaggerEnabled() {
        return environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)
            && environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false);
    }

    /** Líneas del aviso. Puro: sin Spring ni logger. */
    static List<String> render(String appName, int port, String contextPath, boolean swaggerEnabled) {
        String base = "http://localhost:" + port + normalize(contextPath);
        String swagger = swaggerEnabled
            ? "Swagger UI: " + base + "/swagger-ui.html"
            : "Swagger UI: desactivado (SWAGGER_ENABLED=false)";
        return List.of(
            RULE,
            "  >> " + appName + " API lista en " + base,
            "    " + swagger,
            RULE);
    }

    private static String normalize(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || contextPath.equals("/")) {
            return "";
        }
        String path = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
