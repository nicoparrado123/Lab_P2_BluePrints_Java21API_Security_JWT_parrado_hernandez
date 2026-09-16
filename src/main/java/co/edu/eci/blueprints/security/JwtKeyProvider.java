package co.edu.eci.blueprints.security;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Genera un par de llaves RSA 2048 al arrancar.
 * Consecuencia: al reiniciar la aplicacion cambian las llaves y los tokens emitidos antes dejan de ser validos.
 * En produccion las llaves se cargarian de un keystore o de un proveedor de identidad (JWKS).
 */
@Component
public class JwtKeyProvider {

    private KeyPair keyPair;

    @PostConstruct
    void init() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            this.keyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar llave RSA", e);
        }
    }

    public RSAPrivateKey privateKey() { return (RSAPrivateKey) keyPair.getPrivate(); }
    public RSAPublicKey publicKey() { return (RSAPublicKey) keyPair.getPublic(); }
}
