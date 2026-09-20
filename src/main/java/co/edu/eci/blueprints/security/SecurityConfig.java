package co.edu.eci.blueprints.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RsaKeyProperties.class)
public class SecurityConfig {

    private static final String READ = "SCOPE_" + InMemoryUserService.SCOPE_READ;
    private static final String WRITE = "SCOPE_" + InMemoryUserService.SCOPE_WRITE;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JsonAuthenticationEntryPoint entryPoint,
                                           JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            // API REST sin sesion ni cookies: CSRF no aplica y cada peticion trae su token
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Publicos
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/error").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Protegidos por scope (primera barrera, a nivel de URL + metodo HTTP)
                .requestMatchers(HttpMethod.GET, "/api/**").hasAuthority(READ)
                .requestMatchers(HttpMethod.POST, "/api/**").hasAuthority(WRITE)
                .requestMatchers(HttpMethod.PUT, "/api/**").hasAuthority(WRITE)
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasAuthority(WRITE)
                .anyRequest().authenticated()
            )
            // Segunda barrera: @PreAuthorize en cada metodo del controller (MethodSecurityConfig)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Valida firma RS256 con la llave publica, expiracion (con la tolerancia configurada)
     * y que el issuer sea el nuestro.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtKeyProvider keyProvider, RsaKeyProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyProvider.publicKey()).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(props.clockSkewSeconds())),
                new JwtIssuerValidator(props.issuer()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
        RSAKey rsaKey = new RSAKey.Builder(keyProvider.publicKey())
                .privateKey(keyProvider.privateKey())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }
}
