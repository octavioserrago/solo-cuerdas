package ar.solocuerdas.backend.listings;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Endpoints navegables sin cuenta. Viven bajo /api/public/** (la unica zona
// abierta en SecurityConfig) y en un controller aparte del autenticado, para
// que el limite entre "cualquiera puede ver esto" y "requiere token" sea
// evidente en la estructura del codigo, no solo en la config de seguridad.
@RestController
@RequestMapping("/api/public/listings")
public class PublicListingController {

    private final ListingRepository listingRepository;
    private final ListingMediaRepository listingMediaRepository;

    public PublicListingController(ListingRepository listingRepository, ListingMediaRepository listingMediaRepository) {
        this.listingRepository = listingRepository;
        this.listingMediaRepository = listingMediaRepository;
    }

    @GetMapping("/{id}")
    public ListingResponse getById(@PathVariable UUID id) {
        // El filtro por status va en la consulta, no en un if posterior: una
        // publicacion que no esta activa no se puede devolver ni por error.
        Listing listing = listingRepository.findByIdAndStatus(id, "active").orElseThrow();
        return ListingResponse.from(listing, approvedMediaOf(listing.getId()));
    }

    @GetMapping
    public List<ListingResponse> list(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {
        return listingRepository.searchActive(categoryId, brandId, province, city, minPrice, maxPrice).stream()
                .map(listing -> ListingResponse.from(listing, approvedMediaOf(listing.getId())))
                .collect(Collectors.toList());
    }

    private List<MediaResponse> approvedMediaOf(UUID listingId) {
        return listingMediaRepository.findByListingIdAndModerationStatus(listingId, "approved").stream()
                .map(MediaResponse::from)
                .collect(Collectors.toList());
    }
}
