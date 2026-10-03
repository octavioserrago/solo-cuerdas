package ar.solocuerdas.backend.listings;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/listings/{listingId}/media")
public class ListingMediaController {

    private final ListingMediaRepository listingMediaRepository;
    private final ListingRepository listingRepository;
    private final MediaStorageClient mediaStorageClient;

    public ListingMediaController(
            ListingMediaRepository listingMediaRepository,
            ListingRepository listingRepository,
            MediaStorageClient mediaStorageClient) {
        this.listingMediaRepository = listingMediaRepository;
        this.listingRepository = listingRepository;
        this.mediaStorageClient = mediaStorageClient;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID listingId,
            @RequestBody CreateMediaRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Listing listing = listingRepository.findById(listingId).orElseThrow();
        requireOwner(listing, requesterId);

        UUID mediaId = UUID.randomUUID();
        String path = listingId + "/" + mediaId;

        ListingMedia media = new ListingMedia();
        media.setId(mediaId);
        media.setListingId(listingId);
        media.setMediaType(request.mediaType());
        media.setSortOrder(request.sortOrder());
        media.setIsVerificationPhoto(Boolean.TRUE.equals(request.isVerificationPhoto()));
        media.setModerationStatus("pending");
        media.setUrl(mediaStorageClient.publicUrlFor(path));

        ListingMedia saved = listingMediaRepository.save(media);
        String uploadUrl = mediaStorageClient.createSignedUploadUrl(path);

        return new MediaUploadResponse(saved.getId(), uploadUrl, saved.getUrl());
    }

    @PostMapping("/{mediaId}/confirm")
    public MediaResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID listingId,
            @PathVariable UUID mediaId) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Listing listing = listingRepository.findById(listingId).orElseThrow();
        requireOwner(listing, requesterId);

        ListingMedia media = listingMediaRepository.findByIdAndListingId(mediaId, listingId).orElseThrow();

        if (!"pending".equals(media.getModerationStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta media ya no esta pendiente de confirmacion.");
        }

        if (runModerationCheck(media)) {
            media.setModerationStatus("approved");
        } else {
            media.setModerationStatus("rejected");
            mediaStorageClient.deleteObject(listingId + "/" + mediaId);
        }

        ListingMedia saved = listingMediaRepository.save(media);
        return MediaResponse.from(saved);
    }

    private void requireOwner(Listing listing, UUID requesterId) {
        if (!listing.getSellerId().equals(requesterId)) {
            throw new AccessDeniedException("No sos el dueno de esta publicacion.");
        }
    }

    // Stub para el MVP de tesis: siempre aprueba. En produccion (v1.0) se
    // reemplaza por una llamada real a un servicio de moderacion automatica,
    // y el archivo deberia subirse primero a una zona privada, no al bucket
    // publico (ver docs/ARCHITECTURE.md seccion 5, "Moderacion de media").
    private boolean runModerationCheck(ListingMedia media) {
        return true;
    }
}
