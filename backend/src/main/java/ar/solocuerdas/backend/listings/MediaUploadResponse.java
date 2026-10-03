package ar.solocuerdas.backend.listings;

import java.util.UUID;

public record MediaUploadResponse(UUID id, String uploadUrl, String url) {
}
