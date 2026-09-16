package co.edu.eci.blueprints.api;

import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.persistence.BlueprintPersistenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Traduce las excepciones de negocio y de peticion invalida al contrato ApiResponse,
 * en lugar del cuerpo de error por defecto de Spring Boot.
 *
 * Nota: no se captura Exception generica a proposito, para no interceptar las
 * excepciones de Spring Security (AccessDeniedException -> 403, etc.).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BlueprintNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(BlueprintNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BlueprintPersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(BlueprintPersistenceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Falla la validacion de @Valid sobre el cuerpo de la peticion. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, detail.isBlank() ? "invalid request body" : detail);
    }

    /** El cuerpo no es JSON valido, viene vacio o no calza con el tipo esperado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "malformed or missing JSON body");
    }

    /** Un parametro de ruta o de query no se puede convertir al tipo esperado. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "invalid value for parameter '" + ex.getName() + "'");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.of(status.value(), message));
    }
}
