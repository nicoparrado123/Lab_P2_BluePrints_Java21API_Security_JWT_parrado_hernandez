package co.edu.eci.blueprints.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de emision/validacion del JWT (prefijo blueprints.security).
 *
 * @param issuer           valor del claim "iss" que se emite y se exige al validar
 * @param tokenTtlSeconds  vida del token en segundos
 * @param clockSkewSeconds tolerancia de reloj al validar exp/nbf. Spring usa 60 s por defecto;
 *                         aqui el default es 0 para que el token expire exactamente al cumplir el TTL.
 */
@ConfigurationProperties(prefix = "blueprints.security")
public record RsaKeyProperties(String issuer, Long tokenTtlSeconds, Long clockSkewSeconds) {

    public RsaKeyProperties {
        if (issuer == null || issuer.isBlank()) issuer = "https://decsis-eci/blueprints";
        if (tokenTtlSeconds == null || tokenTtlSeconds <= 0) tokenTtlSeconds = 3600L;
        if (clockSkewSeconds == null || clockSkewSeconds < 0) clockSkewSeconds = 0L;
    }
}
