package co.edu.eci.blueprints.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Usuarios de prueba en memoria (login didactico). Cada usuario tiene sus propios scopes:
 * <ul>
 *   <li>student   / student123   -> blueprints.read</li>
 *   <li>assistant / assistant123 -> blueprints.read blueprints.write</li>
 * </ul>
 */
@Service
public class InMemoryUserService {

    public static final String SCOPE_READ = "blueprints.read";
    public static final String SCOPE_WRITE = "blueprints.write";

    private record UserAccount(String passwordHash, List<String> scopes) {}

    private final Map<String, UserAccount> users;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public InMemoryUserService(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.users = Map.of(
            "student", new UserAccount(encoder.encode("student123"), List.of(SCOPE_READ)),
            "assistant", new UserAccount(encoder.encode("assistant123"), List.of(SCOPE_READ, SCOPE_WRITE))
        );
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /**
     * Valida credenciales.
     *
     * @return los scopes del usuario si las credenciales son correctas; vacio en otro caso
     */
    public Optional<List<String>> authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) return Optional.empty();
        UserAccount account = users.get(username);
        if (account == null) {
            // Se compara igual contra un hash falso para que el tiempo de respuesta
            // no revele si el usuario existe o no.
            encoder.matches(rawPassword, dummyHash);
            return Optional.empty();
        }
        return encoder.matches(rawPassword, account.passwordHash())
                ? Optional.of(account.scopes())
                : Optional.empty();
    }
}
