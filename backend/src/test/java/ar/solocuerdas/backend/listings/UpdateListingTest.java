package ar.solocuerdas.backend.listings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import ar.solocuerdas.backend.plans.Plan;
import ar.solocuerdas.backend.plans.PlanRepository;
import ar.solocuerdas.backend.plans.Subscription;
import ar.solocuerdas.backend.plans.SubscriptionRepository;

@WebMvcTest(ListingController.class)
@Import(SecurityConfig.class)
class UpdateListingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingRepository listingRepository;

    @MockitoBean
    private ListingMediaRepository listingMediaRepository;

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private PlanRepository planRepository;

    @Test
    void ownerCanEditOwnListing() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = existingListing(listingId, sellerId, "active");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/listings/{id}", listingId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 140000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(140000));
    }

    @Test
    void nonOwnerCannotEditListing() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = existingListing(listingId, ownerId, "active");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(patch("/api/listings/{id}", listingId)
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 140000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanPauseAnActiveListing() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = existingListing(listingId, sellerId, "active");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/listings/{id}", listingId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"paused\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"));
    }

    @Test
    void ownerCanReactivateWhenUnderQuota() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = existingListing(listingId, sellerId, "paused");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubPlanQuota(sellerId, 1, 2);

        mockMvc.perform(patch("/api/listings/{id}", listingId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void ownerCannotReactivateWhenAtQuota() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = existingListing(listingId, sellerId, "paused");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        stubPlanQuota(sellerId, 2, 2);

        mockMvc.perform(patch("/api/listings/{id}", listingId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"active\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsAStatusValueOutsideActiveAndPaused() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = existingListing(listingId, sellerId, "active");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(patch("/api/listings/{id}", listingId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"sold\"}"))
                .andExpect(status().isBadRequest());
    }

    private Listing existingListing(UUID id, UUID sellerId, String status) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setTitle("Guitarra electrica Fender");
        listing.setStatus(status);
        return listing;
    }

    private void stubPlanQuota(UUID sellerId, long activeListingCount, int maxActiveListings) {
        Subscription subscription = new Subscription();
        subscription.setProfileId(sellerId);
        subscription.setPlanId(1);
        subscription.setStatus("active");

        Plan plan = new Plan();
        plan.setId(1);
        plan.setMaxActiveListings(maxActiveListings);

        when(subscriptionRepository.findByProfileIdAndStatus(sellerId, "active"))
                .thenReturn(Optional.of(subscription));
        when(planRepository.findById(1)).thenReturn(Optional.of(plan));
        when(listingRepository.countBySellerIdAndStatus(sellerId, "active")).thenReturn(activeListingCount);
    }
}
