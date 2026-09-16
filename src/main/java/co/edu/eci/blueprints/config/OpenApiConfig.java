package co.edu.eci.blueprints.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI api() {
        return new OpenAPI()
            .info(new Info()
                .title("BluePrints API")
                .version("2.0")
                .description("""
                    API de blueprints protegida con JWT (OAuth 2.0 Resource Server, RS256).

                    1. Haz login en `POST /auth/login`.
                    2. Copia el `access_token` y pegalo en **Authorize** (solo el token, sin la palabra Bearer).

                    | Usuario | Contrasena | Scopes |
                    |---|---|---|
                    | student | student123 | blueprints.read |
                    | assistant | assistant123 | blueprints.read blueprints.write |
                    """))
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
            .components(new Components().addSecuritySchemes("bearer-jwt",
                new SecurityScheme()
                    .name("bearer-jwt")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
