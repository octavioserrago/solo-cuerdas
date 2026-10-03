package ar.solocuerdas.backend.sales;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSaleRequest(
        UUID listingId,
        UUID buyerId,
        BigDecimal finalPrice,
        String currency) {
}
