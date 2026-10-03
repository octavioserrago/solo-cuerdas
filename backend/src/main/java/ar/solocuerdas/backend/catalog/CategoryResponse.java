package ar.solocuerdas.backend.catalog;

public record CategoryResponse(Integer id, String name) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
