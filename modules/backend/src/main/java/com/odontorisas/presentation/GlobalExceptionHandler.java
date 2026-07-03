package com.odontorisas.presentation;

import com.odontorisas.presentation.dto.ErrorResponse;
import com.odontorisas.service.auth.EmailAlreadyExistsException;
import com.odontorisas.service.auth.InvalidCredentialsException;
import com.odontorisas.service.auth.InvalidRefreshTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapea las excepciones de dominio y de validación a respuestas HTTP consistentes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex) {
        return status(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Error de validación", fieldErrors));
    }

    private ResponseEntity<ErrorResponse> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message));
    }
}
