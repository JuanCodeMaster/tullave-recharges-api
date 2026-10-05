package com.tullave.recharges.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.tullave.recharges.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manejo centralizado de errores. Toda respuesta de error de la API sale con el mismo formato
 * ({@link ErrorResponse}) y se registra con el nivel adecuado: WARN para errores del cliente,
 * ERROR (con stack trace) para fallos inesperados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ErrorResponse.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();

        log.warn("Validación fallida en {} {}: {}", request.getMethod(), request.getRequestURI(), details);
        return build(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex,
                                                               HttpServletRequest request) {
        List<ErrorResponse.FieldError> details = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();

        log.warn("Parámetros inválidos en {} {}: {}", request.getMethod(), request.getRequestURI(), details);
        return build(HttpStatus.BAD_REQUEST, "Los parámetros enviados no son válidos", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        // Caso típico: valor inválido para un enum (paymentMethod) o tipo incorrecto en un campo.
        if (ex.getCause() instanceof InvalidFormatException ife) {
            String field = ife.getPath().stream()
                    .map(ref -> ref.getFieldName())
                    .filter(name -> name != null)
                    .collect(Collectors.joining("."));
            String message = ife.getTargetType().isEnum()
                    ? "Valor no permitido. Valores válidos: " + Arrays.toString(ife.getTargetType().getEnumConstants())
                    : "Tipo de dato inválido";

            log.warn("Cuerpo inválido en {} {}: campo={} valor={}", request.getMethod(), request.getRequestURI(),
                    field, ife.getValue());
            return build(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos", request,
                    List.of(new ErrorResponse.FieldError(field, message)));
        }

        log.warn("Cuerpo ilegible en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud es inválido o está mal formado", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
        var detail = new ErrorResponse.FieldError(ex.getName(), "Se esperaba un valor de tipo " + expected);

        log.warn("Tipo inválido en {} {}: {}", request.getMethod(), request.getRequestURI(), detail);
        return build(HttpStatus.BAD_REQUEST, "Los parámetros enviados no son válidos", request, List.of(detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest request) {
        var detail = new ErrorResponse.FieldError(ex.getParameterName(), "El parámetro es obligatorio");
        log.warn("Parámetro faltante en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "Los parámetros enviados no son válidos", request, List.of(detail));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Recurso no encontrado en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoRoute(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Ruta inexistente: {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "La ruta solicitada no existe", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        log.warn("Método no soportado: {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no soportado para esta ruta", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", request, null);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
                                                       List<ErrorResponse.FieldError> details) {
        var body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details == null || details.isEmpty() ? null : details
        );
        return ResponseEntity.status(status).body(body);
    }

    /** "list.cardNumber" -> "cardNumber": solo interesa el nombre del parámetro, no el método. */
    private static String lastNode(String propertyPath) {
        int idx = propertyPath.lastIndexOf('.');
        return idx >= 0 ? propertyPath.substring(idx + 1) : propertyPath;
    }
}
