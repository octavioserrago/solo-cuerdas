package ar.solocuerdas.backend.reports;

import java.util.UUID;

public record CreateReportRequest(
        UUID listingId,
        UUID reportedProfileId,
        UUID conversationId,
        String reason) {
}
