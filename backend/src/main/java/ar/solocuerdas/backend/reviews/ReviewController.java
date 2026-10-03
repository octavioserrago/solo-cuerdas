package ar.solocuerdas.backend.reviews;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;
import ar.solocuerdas.backend.sales.Sale;
import ar.solocuerdas.backend.sales.SaleRepository;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final SaleRepository saleRepository;
    private final ListingRepository listingRepository;

    public ReviewController(
            ReviewRepository reviewRepository,
            SaleRepository saleRepository,
            ListingRepository listingRepository) {
        this.reviewRepository = reviewRepository;
        this.saleRepository = saleRepository;
        this.listingRepository = listingRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateReviewRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Sale sale = saleRepository.findById(request.saleId()).orElseThrow();

        if (!"completed".equals(sale.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta todavia no esta completada.");
        }

        Listing listing = listingRepository.findById(sale.getListingId()).orElseThrow();
        UUID sellerId = listing.getSellerId();
        UUID buyerId = sale.getBuyerId();

        UUID revieweeId;
        if (requesterId.equals(buyerId)) {
            revieweeId = sellerId;
        } else if (requesterId.equals(sellerId)) {
            revieweeId = buyerId;
        } else {
            throw new AccessDeniedException("No participaste de esta venta.");
        }

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setSaleId(sale.getId());
        review.setReviewerId(requesterId);
        review.setRevieweeId(revieweeId);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setCreatedAt(Instant.now());

        Review saved = reviewRepository.save(review);
        return ReviewResponse.from(saved);
    }
}
