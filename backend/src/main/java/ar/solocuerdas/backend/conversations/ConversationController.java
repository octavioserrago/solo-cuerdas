package ar.solocuerdas.backend.conversations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;
import ar.solocuerdas.backend.sales.SaleRepository;
import ar.solocuerdas.backend.users.Profile;
import ar.solocuerdas.backend.users.ProfileRepository;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ListingRepository listingRepository;
    private final BlockedBuyerRepository blockedBuyerRepository;
    private final ProfileRepository profileRepository;
    private final SaleRepository saleRepository;

    public ConversationController(
            ConversationRepository conversationRepository,
            ListingRepository listingRepository,
            BlockedBuyerRepository blockedBuyerRepository,
            ProfileRepository profileRepository,
            SaleRepository saleRepository) {
        this.conversationRepository = conversationRepository;
        this.listingRepository = listingRepository;
        this.blockedBuyerRepository = blockedBuyerRepository;
        this.profileRepository = profileRepository;
        this.saleRepository = saleRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateConversationRequest request) {
        UUID buyerId = UUID.fromString(jwt.getSubject());
        Listing listing = listingRepository.findById(request.listingId()).orElseThrow();

        if (listing.getSellerId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No podes iniciar una conversacion sobre tu propia publicacion.");
        }
        if (blockedBuyerRepository.existsBySellerIdAndBuyerId(listing.getSellerId(), buyerId)) {
            throw new AccessDeniedException("Este vendedor no acepta contacto tuyo.");
        }

        Optional<Conversation> existing =
                conversationRepository.findByListingIdAndBuyerId(listing.getId(), buyerId);
        if (existing.isPresent()) {
            Conversation conversation = existing.get();
            if (!"rejected".equals(conversation.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una solicitud de contacto en curso con este vendedor.");
            }
            conversation.setStatus("pending");
            conversation.setContactReason(request.contactReason());
            Conversation saved = conversationRepository.save(conversation);
            return ConversationResponse.from(saved, null);
        }

        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setListingId(listing.getId());
        conversation.setBuyerId(buyerId);
        conversation.setStatus("pending");
        conversation.setContactReason(request.contactReason());
        conversation.setCreatedAt(Instant.now());

        Conversation saved = conversationRepository.save(conversation);
        return ConversationResponse.from(saved, null);
    }

    @GetMapping("/me")
    public List<ConversationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());

        List<ConversationResponse> responses = new ArrayList<>();

        List<Conversation> asBuyer = conversationRepository.findByBuyerId(requesterId);
        for (Conversation conversation : asBuyer) {
            responses.add(ConversationResponse.from(conversation, null));
        }

        List<UUID> myListingIds = listingRepository.findBySellerId(requesterId).stream()
                .map(Listing::getId)
                .collect(Collectors.toList());
        List<Conversation> asSeller = myListingIds.isEmpty()
                ? List.of()
                : conversationRepository.findByListingIdIn(myListingIds);
        for (Conversation conversation : asSeller) {
            responses.add(ConversationResponse.from(conversation, buyerSummaryOf(conversation.getBuyerId())));
        }

        return responses;
    }

    @PatchMapping("/{id}")
    public ConversationResponse resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody ResolveConversationRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Conversation conversation = conversationRepository.findById(id).orElseThrow();
        Listing listing = listingRepository.findById(conversation.getListingId()).orElseThrow();

        if (!listing.getSellerId().equals(requesterId)) {
            throw new AccessDeniedException("No sos el vendedor de esta publicacion.");
        }
        if (!"pending".equals(conversation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud ya fue resuelta.");
        }
        if (!"accepted".equals(request.status()) && !"rejected".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede resolver como accepted o rejected.");
        }

        conversation.setStatus(request.status());
        Conversation saved = conversationRepository.save(conversation);
        return ConversationResponse.from(saved, null);
    }

    private BuyerSummary buyerSummaryOf(UUID buyerId) {
        Profile buyerProfile = profileRepository.findById(buyerId).orElseThrow();
        long completedPurchases = saleRepository.countByBuyerIdAndStatus(buyerId, "completed");
        return BuyerSummary.from(buyerProfile, completedPurchases);
    }
}
