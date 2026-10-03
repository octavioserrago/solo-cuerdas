package ar.solocuerdas.backend.users;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicProfileResponse(
        UUID id,
        String username,
        String firstName,
        String province,
        String city,
        String identityStatus,
        BigDecimal ratingAverage,
        Integer ratingCount,
        Instant createdAt) {

    static PublicProfileResponse from(Profile profile) {
        return new PublicProfileResponse(
                profile.getId(),
                profile.getUsername(),
                profile.getFirstName(),
                profile.getProvince(),
                profile.getCity(),
                profile.getIdentityStatus(),
                profile.getRatingAverage(),
                profile.getRatingCount(),
                profile.getCreatedAt());
    }
}
