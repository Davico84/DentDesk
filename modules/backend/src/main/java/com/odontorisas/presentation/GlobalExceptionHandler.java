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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manejo de errores en formato RFC 9457 (ProblemDetail). Extiende
 * {@link ResponseEntityExceptionHandler} para que las excepciones propias de
 * Spring MVC (404, 405, JSON malformado…) conserven su status como ProblemDetail
 * en vez de convertirse en 500. Añade {@code timestamp} y {@code traceId}
 * (del MDC, ver {@link TraceIdFilter}) como extensiones. Ver docs/backend.md §10.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Cualquier excepción de negocio → su status + type/errorType uniforme. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(URI.create("/errors/" + ex.getErrorType()));
        problem.setTitle(ex.getStatus().getReasonPhrase());
        addExtensions(problem);
        return problem;
    }

    /**
     * Colisión con una restricción de unicidad (carrera TOCTOU sobre el correo o el slug de la
     * organización, pese al pre-chequeo y a la desambiguación) → 409, no un 500 genérico.
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setType(URI.create("/errors/" + errorType));
        problem.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
        addExtensions(problem);
        return problem;
    }

    /**
     * Autorización denegada por method security ({@code @PreAuthorize}) → 403. Sin este handler
     * explícito, la {@link AccessDeniedException} —lanzada dentro del dispatcher, no en la cadena
     * de filtros— caería en el catch-all {@code Exception} y devolvería un 500 en vez del 403 que
     * corresponde. (Las denegaciones por URL de {@code authorizeHttpRequests} las resuelve el
     * {@code ExceptionTranslationFilter} antes de llegar aquí.)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción.");
        problem.setType(URI.create("/errors/access-denied"));
        problem.setTitle(HttpStatus.FORBIDDEN.getReasonPhrase());
        addExtensions(problem);
        return problem;
    }

    /** Red de seguridad: cualquier error inesperado → 500 (con log del stacktrace y traceId). */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaught(Exception ex) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        log.error("[trace {}] Excepción no controlada: {}", traceId, ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");
        problem.setType(URI.create("/errors/internal-server-error"));
        problem.setTitle("Internal Server Error");
        addExtensions(problem);
        return problem;
    }

    /** Validación de {@code @Valid} → 400 con la lista de campos inválidos. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Uno o más campos son inválidos.");
        problem.setType(URI.create("/errors/validation-error"));
        problem.setTitle("Validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Valor inválido"))
            .toList();
        problem.setProperty("errors", errors);
        addExtensions(problem);
        return ResponseEntity.badRequest().body(problem);
    }

    private void addExtensions(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now());
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
    }
}
