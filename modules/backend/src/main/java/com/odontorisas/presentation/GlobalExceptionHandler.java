package com.odontorisas.presentation;

import com.odontorisas.infra.config.TraceIdFilter;
import com.odontorisas.service.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Manejo de errores en formato RFC 9457 (ProblemDetail). Extiende
 * {@link ResponseEntityExceptionHandler} para que las excepciones propias de
 * Spring MVC (404, 405, JSON malformado…) conserven su status como ProblemDetail
 * en vez de convertirse en 500. El cuerpo lo construye siempre {@link ProblemDetails},
 * de modo que <b>todos</b> los errores de la API tengan la misma forma. Ver docs/backend.md §10.
 *
 * <p>Aquí aterriza también el {@code 401} de <b>sesión ausente</b>: el entry point de seguridad
 * delega en el {@code HandlerExceptionResolver} de MVC (ver {@code SecurityConfig}) para que ese
 * error lo renderice esta misma maquinaria, y no el cuerpo por defecto del contenedor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Cualquier excepción de negocio → su status + type/errorType uniforme. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        return ProblemDetails.of(ex.getStatus(), ex.getErrorType(), ex.getMessage());
    }

    /**
     * Sesión ausente o inválida (no autenticado). Llega aquí porque el entry point de seguridad
     * delega en el {@code HandlerExceptionResolver}: así este {@code 401} sale con el <b>mismo</b>
     * formato RFC 9457 que el resto, y el contrato puede describirlo con un único schema.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "unauthorized",
            "Necesitas iniciar sesión para acceder a este recurso.");
    }

    /** Autorización denegada por method security ({@code @PreAuthorize}) → 403, no el 500 del catch-all. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, "access-denied",
            "No tienes permiso para realizar esta acción.");
    }

    /**
     * Colisión con una restricción de unicidad (carrera TOCTOU sobre el correo o el slug de la
     * organización, pese al pre-chequeo y a la desambiguación) → 409, no un 500 genérico. El
     * {@code type} discrimina el conflicto, que es como el frontend ramifica.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        String lower = cause == null ? "" : cause.toLowerCase();
        String errorType;
        String detail;
        if (lower.contains("email")) {
            errorType = "email-already-exists";
            detail = "El correo ya está registrado.";
        } else if (lower.contains("slug") || lower.contains("organization")) {
            errorType = "organization-slug-taken";
            detail = "Ya existe una organización con un identificador equivalente. Inténtalo de nuevo.";
        } else {
            errorType = "resource-conflict";
            detail = "El recurso entra en conflicto con uno existente.";
        }
        return ProblemDetails.of(HttpStatus.CONFLICT, errorType, detail);
    }

    /** Red de seguridad: cualquier error inesperado → 500 (con log del stacktrace y traceId). */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaught(Exception ex) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        log.error("[trace {}] Excepción no controlada: {}", traceId, ex.getMessage(), ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error",
            "Ocurrió un error inesperado.");
    }

    /**
     * Validación de {@code @Valid} → 400 con la lista de campos inválidos. Es la única respuesta
     * que añade la extensión {@code errors} (de ahí el schema {@code ValidationProblem}); el otro
     * 400 posible —JSON malformado, que resuelve la clase padre— no la lleva.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(
            HttpStatus.BAD_REQUEST, "validation-error", "Uno o más campos son inválidos.");
        problem.setTitle("Validation failed"); // se conserva el título previo: este change no altera cuerpos
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Valor inválido"))
            .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
