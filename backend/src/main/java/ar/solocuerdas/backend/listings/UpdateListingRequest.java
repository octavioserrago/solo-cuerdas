package ar.solocuerdas.backend.listings;

import java.math.BigDecimal;

public record UpdateListingRequest(
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
        // Solo "active" o "paused" -- cualquier otro valor se rechaza. "sold"
        // lo va a manejar el futuro modulo de sales; "deleted"/"draft" no
        // estan en el alcance de este endpoint.
        String status) {
}
