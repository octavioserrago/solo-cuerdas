package ar.solocuerdas.backend.listings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.solocuerdas.backend.config.SecurityConfig;

@WebMvcTest(ListingMediaController.class)
@Import(SecurityConfig.class)
class ListingMediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingMediaRepository listingMediaRepository;

    @MockitoBean
    private ListingRepository listingRepository;

    @MockitoBean
    private MediaStorageClient mediaStorageClient;

    @Test
    void ownerReservesAnUploadSlot() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = ownedListing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingMediaRepository.save(any(ListingMedia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaStorageClient.publicUrlFor(anyString())).thenReturn("https://storage.example/listing-media/foo.jpg");
        when(mediaStorageClient.createSignedUploadUrl(anyString()))
                .thenReturn("https://storage.example/upload?token=abc");

        mockMvc.perform(post("/api/listings/{listingId}/media", listingId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaType\": \"photo\", \"sortOrder\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value("https://storage.example/upload?token=abc"))
                .andExpect(jsonPath("$.url").value("https://storage.example/listing-media/foo.jpg"));
    }

    @Test
    void nonOwnerCannotReserveAnUploadSlot() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = ownedListing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(post("/api/listings/{listingId}/media", listingId)
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaType\": \"photo\", \"sortOrder\": 1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerConfirmsAnUploadAndItGetsApproved() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Listing listing = ownedListing(listingId, sellerId);
        ListingMedia media = pendingMedia(mediaId, listingId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingMediaRepository.findByIdAndListingId(mediaId, listingId)).thenReturn(Optional.of(media));
        when(listingMediaRepository.save(any(ListingMedia.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/listings/{listingId}/media/{mediaId}/confirm", listingId, mediaId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("approved"));

        verify(mediaStorageClient, org.mockito.Mockito.never()).deleteObject(anyString());
    }

    @Test
    void nonOwnerCannotConfirmAnUpload() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Listing listing = ownedListing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(post("/api/listings/{listingId}/media/{mediaId}/confirm", listingId, mediaId)
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotConfirmMediaThatBelongsToAnotherListing() throws Exception {
        // El dueno de "listingId" es real, pero el mediaId pertenece a OTRA
        // publicacion (ajena) -- no tiene que poder confirmarla igual (IDOR).
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Listing listing = ownedListing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingMediaRepository.findByIdAndListingId(mediaId, listingId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/listings/{listingId}/media/{mediaId}/confirm", listingId, mediaId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotConfirmMediaThatIsNotPending() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Listing listing = ownedListing(listingId, sellerId);
        ListingMedia media = pendingMedia(mediaId, listingId);
        media.setModerationStatus("approved");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingMediaRepository.findByIdAndListingId(mediaId, listingId)).thenReturn(Optional.of(media));

        mockMvc.perform(post("/api/listings/{listingId}/media/{mediaId}/confirm", listingId, mediaId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString()))))
                .andExpect(status().isBadRequest());
    }

    private Listing ownedListing(UUID id, UUID sellerId) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setStatus("active");
        return listing;
    }

    private ListingMedia pendingMedia(UUID id, UUID listingId) {
        ListingMedia media = new ListingMedia();
        media.setId(id);
        media.setListingId(listingId);
        media.setMediaType("photo");
        media.setUrl("https://storage.example/listing-media/" + listingId + "/" + id);
        media.setSortOrder(1);
        media.setIsVerificationPhoto(false);
        media.setModerationStatus("pending");
        return media;
    }
}
