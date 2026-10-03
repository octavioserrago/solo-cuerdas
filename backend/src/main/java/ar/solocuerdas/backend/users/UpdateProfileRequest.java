package ar.solocuerdas.backend.users;

import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        String firstName,
        String lastName,
        // Mismo formato que exige la columna profiles.username en la migracion.
        @Pattern(regexp = "^[A-Za-z0-9_.]{3,30}$", message = "username invalido") String username,
        String phone,
        String province,
        String city) {
}
