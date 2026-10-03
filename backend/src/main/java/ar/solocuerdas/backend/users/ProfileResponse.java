package ar.solocuerdas.backend.users;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String username,
        String phone,
        String province,
        String city,
        String role,
        String identityStatus,
        BigDecimal ratingAverage,
        Integer ratingCount,
        Instant createdAt) {

    static ProfileResponse from(Profile profile, String email) {
        return new ProfileResponse(
                profile.getId(),
                email,
                profile.getFirstName(),
                profile.getLastName(),
                profile.getUsername(),
                profile.getPhone(),
                profile.getProvince(),
                profile.getCity(),
                profile.getRole(),
                profile.getIdentityStatus(),
                profile.getRatingAverage(),
                profile.getRatingCount(),
                profile.getCreatedAt());
    }
}
