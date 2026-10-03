package ar.solocuerdas.backend.catalog;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Catalogos de referencia (categorias, marcas), sin auth -- se seedean en
// la migracion y hoy no hay forma de crearlos/editarlos desde la API.
@RestController
@RequestMapping("/api/public")
public class CatalogController {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public CatalogController(CategoryRepository categoryRepository, BrandRepository brandRepository) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/brands")
    public List<BrandResponse> brands() {
        return brandRepository.findAll().stream()
                .map(BrandResponse::from)
                .collect(Collectors.toList());
    }
}
