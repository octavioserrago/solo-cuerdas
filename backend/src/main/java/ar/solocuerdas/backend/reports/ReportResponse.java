package ar.solocuerdas.backend.reports;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID reporterId,
        UUID listingId,
        UUID reportedProfileId,
        String reason,
        String status,
        UUID resolvedBy,
        Instant createdAt) {

    static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporterId(),
                report.getListingId(),
                report.getReportedProfileId(),
                report.getReason(),
                report.getStatus(),
                report.getResolvedBy(),
                report.getCreatedAt());
    }
}
