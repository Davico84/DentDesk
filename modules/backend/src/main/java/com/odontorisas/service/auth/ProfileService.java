package com.odontorisas.service.auth;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil propio ({@code update-authentication-my-profile} design.md D1/D5). El usuario sale siempre de la
 * sesión ({@link IdentityContext}, poblado desde el token verificado), nunca de la petición: no hay forma de
 * nombrar a otro. El correo es solo de lectura: es la identidad de login.
 *
 * <p>Corregir el nombre no toca lo firmado: la firma guarda su propia copia del nombre
 * ({@code SignerSnapshot}, {@code update-staff-professional-credential}).
 */
@Service
public class ProfileService {

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    public record ProfileView(String fullName, String email) {
        static ProfileView from(User user) {
            return new ProfileView(user.getFullName(), user.getEmail());
        }
    }

    @Transactional(readOnly = true)
    public ProfileView mine() {
        return ProfileView.from(current());
    }

    /** {@code fullName} ya llega sin espacios en los extremos y validado (2–120) por el DTO. */
    @Transactional
    public ProfileView updateFullName(String fullName) {
        User user = current();
        user.setFullName(fullName);
        return ProfileView.from(user);
    }

    private User current() {
        Long userId = IdentityContext.get();
        if (userId == null) {
            // El filtro solo deja pasar peticiones autenticadas con identidad; esto sería un bug de cableado.
            throw new IllegalStateException("Perfil propio sin identidad en la sesión.");
        }
        return users.findById(userId)
            .orElseThrow(() -> new IllegalStateException("La identidad de la sesión no existe."));
    }
}
