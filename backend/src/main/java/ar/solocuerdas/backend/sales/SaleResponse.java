package ar.solocuerdas.backend.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        UUID listingId,
        UUID buyerId,
        BigDecimal finalPrice,
        String currency,
        String status,
        // Solo tiene valor mientras status = pending_confirmation (el vendedor
        // se lo pasa al comprador en persona). Queda en null el resto del tiempo.
        String confirmationCode,
        Instant createdAt,
        Instant completedAt) {

    static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getListingId(),
                sale.getBuyerId(),
                sale.getFinalPrice(),
                sale.getCurrency(),
                sale.getStatus(),
                sale.getConfirmationCode(),
                sale.getCreatedAt(),
                sale.getCompletedAt());
    }
}
