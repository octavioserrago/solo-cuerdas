package ar.solocuerdas.backend.sales;

import java.security.SecureRandom;
import java.time.Instant;
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

import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SaleRepository saleRepository;
    private final ListingRepository listingRepository;

    public SaleController(SaleRepository saleRepository, ListingRepository listingRepository) {
        this.saleRepository = saleRepository;
        this.listingRepository = listingRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateSaleRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Listing listing = listingRepository.findById(request.listingId()).orElseThrow();

        if (!listing.getSellerId().equals(requesterId)) {
            throw new AccessDeniedException("No sos el vendedor de esta publicacion.");
        }
        if (!"active".equals(listing.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La publicacion no esta activa.");
        }

        Sale sale = new Sale();
        sale.setId(UUID.randomUUID());
        sale.setListingId(listing.getId());
        sale.setBuyerId(request.buyerId());
        sale.setFinalPrice(request.finalPrice());
        sale.setCurrency(request.currency());
        sale.setStatus("pending_confirmation");
        sale.setConfirmationCode(generateConfirmationCode());
        sale.setCreatedAt(Instant.now());

        Sale saved = saleRepository.save(sale);
        return SaleResponse.from(saved);
    }

    @PostMapping("/{id}/confirm")
    public SaleResponse confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody ConfirmSaleRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Sale sale = saleRepository.findById(id).orElseThrow();

        if (!sale.getBuyerId().equals(requesterId)) {
            throw new AccessDeniedException("No sos el comprador de esta venta.");
        }
        if (!"pending_confirmation".equals(sale.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta no esta pendiente de confirmacion.");
        }
        if (!request.confirmationCode().equals(sale.getConfirmationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El codigo no coincide.");
        }

        sale.setStatus("completed");
        sale.setCompletedAt(Instant.now());
        sale.setConfirmationCode(null);
        Sale saved = saleRepository.save(sale);

        Listing listing = listingRepository.findById(sale.getListingId()).orElseThrow();
        listing.setStatus("sold");
        listingRepository.save(listing);

        return SaleResponse.from(saved);
    }

    private static String generateConfirmationCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
