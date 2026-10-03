package ar.solocuerdas.backend.reviews;

import java.util.UUID;

public record CreateReviewRequest(
        UUID saleId,
        Short rating,
        String comment) {
}
