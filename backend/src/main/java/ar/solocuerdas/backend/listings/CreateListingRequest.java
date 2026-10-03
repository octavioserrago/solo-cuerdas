package ar.solocuerdas.backend.listings;

import java.math.BigDecimal;

public record CreateListingRequest(
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
        String city) {
}
