package ar.solocuerdas.backend.listings;

import java.util.UUID;

public record MediaResponse(
        UUID id,
        UUID listingId,
        String mediaType,
        String url,
        Integer sortOrder,
        Boolean isVerificationPhoto,
        String moderationStatus) {

    static MediaResponse from(ListingMedia media) {
        return new MediaResponse(
                media.getId(),
                media.getListingId(),
                media.getMediaType(),
                media.getUrl(),
                media.getSortOrder(),
                media.getIsVerificationPhoto(),
                media.getModerationStatus());
    }
}
