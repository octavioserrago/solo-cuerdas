package ar.solocuerdas.backend.listings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
class ListingControllerTest {

    private static final String VALID_BODY = """
            {
              "categoryId": 1,
              "title": "Guitarra electrica Fender",
              "itemCondition": "excellent",
              "price": 150000,
              "currency": "ARS",
              "province": "Buenos Aires",
              "city": "La Plata"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingRepository listingRepository;

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private PlanRepository planRepository;

    @Test
    void createListingPersistsAsActive() throws Exception {
        UUID sellerId = UUID.randomUUID();
        stubPlanQuota(sellerId, 0, 2);
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.title").value("Guitarra electrica Fender"))
                .andExpect(jsonPath("$.price").value(150000));
    }

    @Test
    void createListingRejectsWhenSellerReachedPlanQuota() throws Exception {
        UUID sellerId = UUID.randomUUID();
        stubPlanQuota(sellerId, 2, 2);

        mockMvc.perform(post("/api/listings")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void listsOwnListingsInAnyStatus() throws Exception {
        UUID sellerId = UUID.randomUUID();

        Listing active = new Listing();
        active.setId(UUID.randomUUID());
        active.setSellerId(sellerId);
        active.setTitle("Guitarra activa");
        active.setStatus("active");

        Listing paused = new Listing();
        paused.setId(UUID.randomUUID());
        paused.setSellerId(sellerId);
        paused.setTitle("Bajo pausado");
        paused.setStatus("paused");

        when(listingRepository.findBySellerId(sellerId)).thenReturn(List.of(active, paused));

        mockMvc.perform(get("/api/listings/me")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[1].status").value("paused"));
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
