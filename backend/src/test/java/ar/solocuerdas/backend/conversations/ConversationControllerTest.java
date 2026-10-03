package ar.solocuerdas.backend.conversations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;
import ar.solocuerdas.backend.sales.SaleRepository;
import ar.solocuerdas.backend.users.Profile;
import ar.solocuerdas.backend.users.ProfileRepository;

@WebMvcTest(ConversationController.class)
@Import(SecurityConfig.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationRepository conversationRepository;

    @MockitoBean
    private ListingRepository listingRepository;

    @MockitoBean
    private BlockedBuyerRepository blockedBuyerRepository;

    @MockitoBean
    private ProfileRepository profileRepository;

    @MockitoBean
    private SaleRepository saleRepository;

    @Test
    void buyerCreatesNewContactRequest() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(blockedBuyerRepository.existsBySellerIdAndBuyerId(sellerId, buyerId)).thenReturn(false);
        when(conversationRepository.findByListingIdAndBuyerId(listingId, buyerId)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/conversations")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\": \"" + listingId + "\", \"contactReason\": \"quiero_comprarlo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.contactReason").value("quiero_comprarlo"))
                .andExpect(jsonPath("$.buyer").doesNotExist());
    }

    @Test
    void sellerCannotRequestContactOnTheirOwnListing() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(post("/api/conversations")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\": \"" + listingId + "\", \"contactReason\": \"consulta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blockedBuyerCannotCreateRequest() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(blockedBuyerRepository.existsBySellerIdAndBuyerId(sellerId, buyerId)).thenReturn(true);

        mockMvc.perform(post("/api/conversations")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\": \"" + listingId + "\", \"contactReason\": \"consulta\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDuplicateAPendingRequest() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);
        Conversation existing = conversation(UUID.randomUUID(), listingId, buyerId, "pending");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(blockedBuyerRepository.existsBySellerIdAndBuyerId(sellerId, buyerId)).thenReturn(false);
        when(conversationRepository.findByListingIdAndBuyerId(listingId, buyerId)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/conversations")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\": \"" + listingId + "\", \"contactReason\": \"consulta\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reopeningARejectedRequestGoesBackToPending() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);
        Conversation existing = conversation(UUID.randomUUID(), listingId, buyerId, "rejected");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(blockedBuyerRepository.existsBySellerIdAndBuyerId(sellerId, buyerId)).thenReturn(false);
        when(conversationRepository.findByListingIdAndBuyerId(listingId, buyerId)).thenReturn(Optional.of(existing));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/conversations")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\": \"" + listingId + "\", \"contactReason\": \"quiero_comprarlo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.contactReason").value("quiero_comprarlo"));
    }

    @Test
    void sellerSeesBuyerSummaryWhenListingMine() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);
        Conversation conversation = conversation(UUID.randomUUID(), listingId, buyerId, "pending");

        Profile buyerProfile = new Profile();
        buyerProfile.setId(buyerId);
        buyerProfile.setFirstName("Juan");
        buyerProfile.setProvince("Chaco");
        buyerProfile.setCity("Resistencia");
        buyerProfile.setRatingAverage(new BigDecimal("4.5"));
        buyerProfile.setRatingCount(2);
        buyerProfile.setIdentityStatus("unverified");

        when(conversationRepository.findByBuyerId(sellerId)).thenReturn(List.of());
        when(listingRepository.findBySellerId(sellerId)).thenReturn(List.of(listing));
        when(conversationRepository.findByListingIdIn(List.of(listingId))).thenReturn(List.of(conversation));
        when(profileRepository.findById(buyerId)).thenReturn(Optional.of(buyerProfile));
        when(saleRepository.countByBuyerIdAndStatus(buyerId, "completed")).thenReturn(3L);

        mockMvc.perform(get("/api/conversations/me")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buyer.province").value("Chaco"))
                .andExpect(jsonPath("$[0].buyer.identityStatus").value("unverified"))
                .andExpect(jsonPath("$[0].buyer.completedPurchases").value(3));
    }

    @Test
    void buyerDoesNotSeeBuyerSummaryOnOwnRequests() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Conversation conversation = conversation(UUID.randomUUID(), listingId, buyerId, "pending");

        when(conversationRepository.findByBuyerId(buyerId)).thenReturn(List.of(conversation));
        when(listingRepository.findBySellerId(buyerId)).thenReturn(List.of());

        mockMvc.perform(get("/api/conversations/me")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buyer").doesNotExist());
    }

    @Test
    void sellerAcceptsAPendingRequest() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);
        Conversation conversation = conversation(conversationId, listingId, buyerId, "pending");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/conversations/{id}", conversationId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void nonSellerCannotResolveARequest() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);
        Conversation conversation = conversation(conversationId, listingId, buyerId, "pending");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(patch("/api/conversations/{id}", conversationId)
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"accepted\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotResolveARequestThatIsNotPending() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Listing listing = listing(listingId, sellerId);
        Conversation conversation = conversation(conversationId, listingId, buyerId, "accepted");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(patch("/api/conversations/{id}", conversationId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"rejected\"}"))
                .andExpect(status().isBadRequest());
    }

    private Listing listing(UUID id, UUID sellerId) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setStatus("active");
        return listing;
    }

    private Conversation conversation(UUID id, UUID listingId, UUID buyerId, String status) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setListingId(listingId);
        conversation.setBuyerId(buyerId);
        conversation.setStatus(status);
        conversation.setContactReason("consulta");
        conversation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return conversation;
    }
}
