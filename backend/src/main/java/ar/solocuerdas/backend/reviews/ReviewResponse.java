package ar.solocuerdas.backend.reviews;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID saleId,
        UUID reviewerId,
        UUID revieweeId,
        Short rating,
        String comment,
        Instant createdAt) {

    static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getSaleId(),
                review.getReviewerId(),
                review.getRevieweeId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
