package ar.solocuerdas.backend.conversations;

import java.math.BigDecimal;
import java.util.UUID;

import ar.solocuerdas.backend.users.Profile;

// Lo que el vendedor ve del comprador antes de aceptar o rechazar una
// solicitud de contacto: solo va en la respuesta cuando quien pide
// GET /api/conversations/me es el vendedor (el comprador ya tiene el
// perfil del vendedor disponible via el listing).
public record BuyerSummary(
        UUID profileId,
        String firstName,
        String lastName,
        String province,
        String city,
        BigDecimal ratingAverage,
        Integer ratingCount,
        String identityStatus,
        long completedPurchases) {

    static BuyerSummary from(Profile profile, long completedPurchases) {
        return new BuyerSummary(
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getProvince(),
                profile.getCity(),
                profile.getRatingAverage(),
                profile.getRatingCount(),
                profile.getIdentityStatus(),
                completedPurchases);
    }
}
