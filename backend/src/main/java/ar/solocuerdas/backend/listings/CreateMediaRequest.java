package ar.solocuerdas.backend.listings;

public record CreateMediaRequest(
        String mediaType,
        Integer sortOrder,
        Boolean isVerificationPhoto) {
}
