package ar.solocuerdas.backend.listings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListingResponse(
        UUID id,
        UUID sellerId,
        Integer categoryId,
        Integer brandId,
        String model,
        String title,
        String description,
        Integer manufactureYear,
        String origin,
        String serialNumber,
        String itemCondition,
        BigDecimal price,
        String currency,
        String province,
        String city,
        String status,
        Instant featuredUntil,
        Instant createdAt,
        // El llamador decide que lista de media pasar: toda (vista del dueno)
        // o solo la aprobada (vista publica) -- este record no filtra nada.
        List<MediaResponse> media) {

    static ListingResponse from(Listing listing, List<MediaResponse> media) {
        return new ListingResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getCategoryId(),
                listing.getBrandId(),
                listing.getModel(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getManufactureYear(),
                listing.getOrigin(),
                listing.getSerialNumber(),
                listing.getItemCondition(),
                listing.getPrice(),
                listing.getCurrency(),
                listing.getProvince(),
                listing.getCity(),
                listing.getStatus(),
                listing.getFeaturedUntil(),
                listing.getCreatedAt(),
                media);
    }
}
