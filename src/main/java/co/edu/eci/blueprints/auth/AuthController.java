package co.edu.eci.blueprints.auth;

import co.edu.eci.blueprints.api.ApiResponse;
import co.edu.eci.blueprints.security.InMemoryUserService;
import co.edu.eci.blueprints.security.RsaKeyProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Login didactico: emite el JWT para consumir el resto de la API.")
public class AuthController {

    private final JwtEncoder encoder;
    private final InMemoryUserService userService;
    private final RsaKeyProperties props;

    public AuthController(JwtEncoder encoder, InMemoryUserService userService, RsaKeyProperties props) {
        this.encoder = encoder;
        this.userService = userService;
        this.props = props;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /** Formato estilo OAuth 2.0 (RFC 6749, seccion 5.1). */
    public record TokenResponse(String access_token, String token_type, long expires_in, String scope) {}

    @Operation(
        summary = "Login",
        description = "Recibe usuario y contrasena y devuelve un JWT firmado con RS256. "
                    + "Usuarios: `student / student123` (blueprints.read) y "
                    + "`assistant / assistant123` (blueprints.read blueprints.write)."
    )
    @SecurityRequirements // lista vacia: este endpoint no requiere token
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token emitido"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Falta username o password"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Credenciales invalidas")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        Optional<List<String>> scopes = userService.authenticate(req.username(), req.password());
        if (scopes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.of(401, "invalid_credentials"));
        }

        Instant now = Instant.now();
        long ttl = props.tokenTtlSeconds();
        String scope = String.join(" ", scopes.get());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttl))
                .subject(req.username())
                .id(UUID.randomUUID().toString())
                .claim("scope", scope)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return ResponseEntity.ok(new TokenResponse(token, "Bearer", ttl, scope));
    }
}
