package ru.diplom.cicd.master.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.diplom.cicd.master.api.dto.ErrorResponse;
import ru.diplom.cicd.master.config.CorrelationIdFilter;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final SensitiveDataSanitizer sensitiveDataSanitizer;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        if (exception.getStatus().is5xxServerError()) {
            log.error("API exception: status={}, code={}, path={}, correlationId={}",
                    exception.getStatus(), exception.getCode(), request.getRequestURI(), correlationId, exception);
        } else {
            log.debug("API exception: status={}, code={}, path={}, correlationId={}, message={}",
                    exception.getStatus(), exception.getCode(), request.getRequestURI(), correlationId, exception.getMessage());
        }
        Object details = exception.getDetails() == null ? null : sensitiveDataSanitizer.redact(exception.getDetails());
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(
                        new ErrorResponse.ErrorBody(exception.getCode(), exception.getMessage(), details),
                        correlationId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(
                new ErrorResponse.ErrorBody("validation_error", "Validation failed", details),
                correlationId(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                new ErrorResponse.ErrorBody("validation_error", "Validation failed", null),
                correlationId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        log.error("Unhandled exception: path={}, correlationId={}", request.getRequestURI(), correlationId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                new ErrorResponse.ErrorBody("internal_error", "Unexpected internal error", null),
                correlationId));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
        return value == null ? null : value.toString();
    }
}
