package ar.solocuerdas.backend.catalog;

public record BrandResponse(Integer id, String name) {

    static BrandResponse from(Brand brand) {
        return new BrandResponse(brand.getId(), brand.getName());
    }
}
