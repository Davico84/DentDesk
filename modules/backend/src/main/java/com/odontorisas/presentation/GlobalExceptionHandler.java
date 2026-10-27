package com.odontorisas.presentation;

import com.odontorisas.infra.config.TraceIdFilter;
import com.odontorisas.service.BusinessException;
import com.odontorisas.service.clinicalhistory.ChecklistIncompleteException;
import com.odontorisas.service.consultationnote.ConsultationNoteChecklistIncompleteException;
import com.odontorisas.service.dentalchart.DentalChartChecklistIncompleteException;
import com.odontorisas.service.patient.DocumentAlreadyExistsException;
import com.odontorisas.service.staff.ClinicalCapacityRequiredException;
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
     * Firma sin capacidad clínica (403) con el motivo ({@code reason}) y, si venció, la fecha
     * ({@code expiredOn}): el cliente explica por qué no se puede firmar
     * ({@code update-staff-professional-credential} design.md D3).
     */
    @ExceptionHandler(ClinicalCapacityRequiredException.class)
    public ProblemDetail handleClinicalCapacityRequired(ClinicalCapacityRequiredException ex) {
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getErrorType(), ex.getMessage());
        problem.setProperty("reason", ex.getReason().name());
        if (ex.getExpiredOn() != null) {
            problem.setProperty("expiredOn", ex.getExpiredOn().toString());
        }
        return problem;
    }

    /**
     * Documento duplicado (409) que además <b>identifica la ficha existente</b> para que el cliente
     * pueda ofrecer abrirla (update-patients D3/D4). No pasa por {@link #handleBusiness} porque
     * necesita una extensión ({@code existingPatientId}) que la traducción genérica no lleva —
     * mismo patrón que {@link #handleMethodArgumentNotValid} con {@code errors}.
     */
    @ExceptionHandler(DocumentAlreadyExistsException.class)
    public ProblemDetail handleDocumentAlreadyExists(DocumentAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getErrorType(), ex.getMessage());
        problem.setProperty("existingPatientId", ex.getExistingPatientId());
        return problem;
    }

    /**
     * Checklist incompleto (400) que además indica <b>qué campos faltan</b> (design.md D4 de
     * `add-clinical-history`) — mismo patrón que {@link #handleDocumentAlreadyExists}: una
     * extensión propia no pasa por {@link #handleBusiness}.
     */
    @ExceptionHandler(ChecklistIncompleteException.class)
    public ProblemDetail handleChecklistIncomplete(ChecklistIncompleteException ex) {
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getErrorType(), ex.getMessage());
        problem.setProperty("missingFields", ex.getMissingFields());
        return problem;
    }

    /** Checklist del odontograma incompleto (400) — mismo patrón que {@link #handleChecklistIncomplete}. */
    @ExceptionHandler(DentalChartChecklistIncompleteException.class)
    public ProblemDetail handleDentalChartChecklistIncomplete(DentalChartChecklistIncompleteException ex) {
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getErrorType(), ex.getMessage());
        problem.setProperty("missingFields", ex.getMissingFields());
        return problem;
    }

    /** Contenido obligatorio de la nota de consulta incompleto (400) — mismo patrón que {@link #handleChecklistIncomplete}. */
    @ExceptionHandler(ConsultationNoteChecklistIncompleteException.class)
    public ProblemDetail handleConsultationNoteChecklistIncomplete(ConsultationNoteChecklistIncompleteException ex) {
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getErrorType(), ex.getMessage());
        problem.setProperty("missingFields", ex.getMissingFields());
        return problem;
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
     * Mensaje en español, apto para el usuario final, por <b>status</b> — no por tipo de
     * excepción. Cubre lo que resuelve la clase padre de {@link ResponseEntityExceptionHandler}
     * (404, 405, JSON malformado, 415, 406, 413, 503…), que de otro modo emitiría su mensaje
     * interno en inglés (p. ej. {@code "Failed to read request"}, verificado en vivo).
     *
     * <p><b>Es un fallback por status, no una traducción por excepción</b>: dos tipos de excepción
     * distintos con el mismo status comparten mensaje a propósito (p. ej. {@code
     * HttpMessageNotReadableException} y {@code MissingServletRequestParameterException} son
     * ambas {@code 400}). Un caso que de verdad necesite su propio texto se resuelve con un
     * {@code @ExceptionHandler} puntual para ese tipo (que ya no pasa por este mapa, ver
     * {@link #handleExceptionInternal}) — no ampliando este switch con matices por excepción.
     *
     * <p>Un status se mapea aquí solo cuando el genérico sería <b>engañoso</b> (p. ej. un
     * {@code 503} no es "tu petición estaba mal", es "reintentá luego"), no simplemente cuando
     * sería menos preciso: el genérico ya cumple la garantía de no filtrar texto interno.
     */
    private static final String DETAIL_GENERICO = "No se pudo procesar la petición.";

    static String detailParaStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "La petición no es válida.";
            case NOT_FOUND -> "El recurso solicitado no existe.";
            case METHOD_NOT_ALLOWED -> "Operación no permitida para este recurso.";
            case UNSUPPORTED_MEDIA_TYPE -> "El tipo de contenido enviado no es compatible.";
            case NOT_ACCEPTABLE -> "El formato solicitado no es compatible.";
            case PAYLOAD_TOO_LARGE -> "El archivo o los datos enviados son demasiado grandes.";
            case SERVICE_UNAVAILABLE ->
                "El servicio no está disponible temporalmente. Inténtalo de nuevo en unos minutos.";
            default -> DETAIL_GENERICO;
        };
    }

    /**
     * Las excepciones que resuelve la clase padre (404, 405, JSON malformado…) construyen su
     * {@link ProblemDetail} por su cuenta, <b>sin pasar por {@link ProblemDetails}</b>: saldrían
     * sin {@code timestamp} ni {@code traceId} y el contrato dejaría de ser fiel (documenta
     * {@code timestamp} como siempre presente). Aquí se les añaden las extensiones comunes, de
     * modo que <b>todos</b> los errores de la API —sin excepción— compartan forma.
     *
     * <p>Además reemplazan su {@code detail} (el mensaje interno de Spring) por uno en español
     * apto para el usuario ({@link #detailParaStatus}), porque el cliente muestra {@code detail}
     * tal cual en un toast: un mensaje interno filtrado llegaría íntegro al usuario final.
     * {@code handleExceptionInternal} recibe un {@link HttpStatusCode}, no el enum {@link
     * HttpStatus}; se resuelve con {@link HttpStatus#resolve(int)}, que devuelve {@code null}
     * para un status no estándar — en ese caso también cae en el mensaje genérico.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setDetail(status != null ? detailParaStatus(status) : DETAIL_GENERICO);
            ProblemDetails.addExtensions(problem);
        }
        return response;
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
