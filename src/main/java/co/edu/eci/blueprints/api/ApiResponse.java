package co.edu.eci.blueprints.api;

/**
 * Contrato uniforme de respuesta de la API (heredado del P1).
 *
 * @param code    codigo HTTP de la respuesta
 * @param message mensaje legible
 * @param data    carga util; null cuando no aplica
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> of(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public static ApiResponse<Void> of(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
