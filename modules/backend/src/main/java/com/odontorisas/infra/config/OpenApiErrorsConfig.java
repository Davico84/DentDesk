package com.odontorisas.infra.config;

import com.odontorisas.infra.security.PublicPaths;
import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.ValidationProblem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * Documenta en el contrato los errores <b>transversales</b> de la API — <b>por defecto, no por
 * disciplina</b>.
 *
 * <p>Repetir las anotaciones de error en cada método no escala y se acaba olvidando: el contrato
 * volvería a desincronizarse. En vez de eso, aquí se inyectan automáticamente a partir de lo que
 * ya se sabe de cada operación:
 *
 * <table border="1">
 *   <caption>Reglas</caption>
 *   <tr><th>Respuesta</th><th>A qué operaciones</th><th>Cómo se decide</th></tr>
 *   <tr><td>{@code 500}</td><td>todas</td><td>siempre (el catch-all cubre todo)</td></tr>
 *   <tr><td>{@code 401}</td><td>las que requieren sesión</td><td>la ruta no está en {@link PublicPaths}</td></tr>
 *   <tr><td>{@code 403}</td><td>las que exigen rol</td><td>el método lleva {@code @PreAuthorize}</td></tr>
 * </table>
 *
 * <p>Consecuencia: cuando una capacidad futura ({@code patients}, {@code appointments}…) añada un
 * endpoint protegido, <b>hereda sus errores sin escribir una línea</b>. Solo declara lo que únicamente
 * su autor sabe (el status de éxito, el {@code 400}, el {@code 409}).
 *
 * <p>Nunca sobrescribe una respuesta ya declarada en la operación: lo explícito manda.
 */
@Configuration
public class OpenApiErrorsConfig {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String API_PROBLEM_REF = "#/components/schemas/ApiProblem";

    /**
     * Registra los schemas de error en {@code components} para que los {@code $ref} que inyecta el
     * customizer siempre resuelvan, aunque ninguna anotación los mencione todavía.
     */
    @Bean
    public OpenApiCustomizer errorSchemasCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                return;
            }
            ModelConverters converters = ModelConverters.getInstance();
            converters.readAll(ApiProblem.class).forEach(openApi.getComponents()::addSchemas);
            converters.readAll(ValidationProblem.class).forEach(openApi.getComponents()::addSchemas);
        };
    }

    /** Inyecta los errores transversales en cada operación, según las reglas de arriba. */
    @Bean
    public OperationCustomizer transversalErrorsCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }

            addIfAbsent(responses, "500", "Error inesperado del servidor.");

            if (!PublicPaths.isPublic(pathOf(handlerMethod))) {
                addIfAbsent(responses, "401", "No autenticado: falta la sesión o no es válida.");
            }
            if (requiresRole(handlerMethod)) {
                addIfAbsent(responses, "403", "Autenticado, pero sin el rol necesario para esta operación.");
            }
            return operation;
        };
    }

    /** Lo declarado explícitamente en la operación manda: no se pisa. */
    private static void addIfAbsent(ApiResponses responses, String code, String description) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, problemResponse(description));
        }
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse()
            .description(description)
            .content(new Content().addMediaType(PROBLEM_JSON,
                new io.swagger.v3.oas.models.media.MediaType()
                    .schema(new Schema<>().$ref(API_PROBLEM_REF))));
    }

    /** Ruta completa de la operación (clase + método), para contrastarla con {@link PublicPaths}. */
    private static String pathOf(HandlerMethod handlerMethod) {
        String base = firstPathOf(
            AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequestMapping.class));
        String sub = firstPathOf(
            AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequestMapping.class));
        String path = base + sub;
        return path.isEmpty() ? "/" : path;
    }

    private static String firstPathOf(RequestMapping mapping) {
        if (mapping == null || mapping.value().length == 0 || mapping.value()[0].isEmpty()) {
            return "";
        }
        return mapping.value()[0];
    }

    /** {@code true} si la operación exige un rol (method security), y por tanto puede dar 403. */
    private static boolean requiresRole(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PreAuthorize.class)
            || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
    }
}
