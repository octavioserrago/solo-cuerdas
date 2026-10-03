package ar.solocuerdas.backend.listings;

import java.time.Instant;
import java.util.List;
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

import ar.solocuerdas.backend.plans.Plan;
import ar.solocuerdas.backend.plans.PlanRepository;
import ar.solocuerdas.backend.plans.Subscription;
import ar.solocuerdas.backend.plans.SubscriptionRepository;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingRepository listingRepository;
    private final ListingMediaRepository listingMediaRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public ListingController(
            ListingRepository listingRepository,
            ListingMediaRepository listingMediaRepository,
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository) {
        this.listingRepository = listingRepository;
        this.listingMediaRepository = listingMediaRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateListingRequest request) {
        UUID sellerId = UUID.fromString(jwt.getSubject());
        requireUnderActiveListingsQuota(sellerId);

        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setSellerId(sellerId);
        listing.setCategoryId(request.categoryId());
        listing.setBrandId(request.brandId());
        listing.setModel(request.model());
        listing.setTitle(request.title());
        listing.setDescription(request.description());
        listing.setManufactureYear(request.manufactureYear());
        listing.setOrigin(request.origin());
        listing.setSerialNumber(request.serialNumber());
        listing.setItemCondition(request.itemCondition());
        listing.setPrice(request.price());
        listing.setCurrency(request.currency());
        listing.setProvince(request.province());
        listing.setCity(request.city());
        listing.setStatus("active");
        listing.setCreatedAt(Instant.now());

        // Recien creada: no puede tener media todavia (se sube aparte,
        // despues de que la publicacion ya existe).
        Listing saved = listingRepository.save(listing);
        return ListingResponse.from(saved, List.of());
    }

    @GetMapping("/me")
    public List<ListingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        UUID sellerId = UUID.fromString(jwt.getSubject());
        return listingRepository.findBySellerId(sellerId).stream()
                .map(listing -> ListingResponse.from(listing, ownMediaOf(listing.getId())))
                .collect(Collectors.toList());
    }

    @PatchMapping("/{id}")
    public ListingResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody UpdateListingRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Listing listing = listingRepository.findById(id).orElseThrow();

        if (!listing.getSellerId().equals(requesterId)) {
            throw new AccessDeniedException("No sos el dueno de esta publicacion.");
        }

        if (request.status() != null) {
            applyStatusChange(listing, request.status());
        }
        if (request.categoryId() != null) {
            listing.setCategoryId(request.categoryId());
        }
        if (request.brandId() != null) {
            listing.setBrandId(request.brandId());
        }
        if (request.model() != null) {
            listing.setModel(request.model());
        }
        if (request.title() != null) {
            listing.setTitle(request.title());
        }
        if (request.description() != null) {
            listing.setDescription(request.description());
        }
        if (request.manufactureYear() != null) {
            listing.setManufactureYear(request.manufactureYear());
        }
        if (request.origin() != null) {
            listing.setOrigin(request.origin());
        }
        if (request.serialNumber() != null) {
            listing.setSerialNumber(request.serialNumber());
        }
        if (request.itemCondition() != null) {
            listing.setItemCondition(request.itemCondition());
        }
        if (request.price() != null) {
            listing.setPrice(request.price());
        }
        if (request.currency() != null) {
            listing.setCurrency(request.currency());
        }
        if (request.province() != null) {
            listing.setProvince(request.province());
        }
        if (request.city() != null) {
            listing.setCity(request.city());
        }

        Listing saved = listingRepository.save(listing);
        return ListingResponse.from(saved, ownMediaOf(saved.getId()));
    }

    private List<MediaResponse> ownMediaOf(UUID listingId) {
        return listingMediaRepository.findByListingId(listingId).stream()
                .map(MediaResponse::from)
                .collect(Collectors.toList());
    }

    private void applyStatusChange(Listing listing, String requestedStatus) {
        if (!requestedStatus.equals("active") && !requestedStatus.equals("paused")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede cambiar status entre active y paused desde este endpoint.");
        }

        boolean isReactivating = requestedStatus.equals("active") && listing.getStatus().equals("paused");
        if (isReactivating) {
            requireUnderActiveListingsQuota(listing.getSellerId());
        }

        listing.setStatus(requestedStatus);
    }

    private void requireUnderActiveListingsQuota(UUID sellerId) {
        Subscription subscription = subscriptionRepository.findByProfileIdAndStatus(sellerId, "active")
                .orElseThrow();
        Plan plan = planRepository.findById(subscription.getPlanId()).orElseThrow();
        long activeListings = listingRepository.countBySellerIdAndStatus(sellerId, "active");

        if (activeListings >= plan.getMaxActiveListings()) {
            throw new ListingQuotaExceededException();
        }
    }
}
