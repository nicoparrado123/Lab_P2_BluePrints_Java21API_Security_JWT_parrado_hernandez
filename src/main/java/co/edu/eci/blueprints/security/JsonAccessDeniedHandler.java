package co.edu.eci.blueprints.security;

import co.edu.eci.blueprints.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 cuando el token es valido pero no tiene el scope requerido.
 * Mantiene la cabecera WWW-Authenticate (error="insufficient_scope") y agrega un cuerpo ApiResponse.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper mapper;

    public JsonAccessDeniedHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException, ServletException {
        delegate.handle(request, response, ex);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),
                ApiResponse.of(403, "forbidden: the token does not have the required scope"));
    }
}
