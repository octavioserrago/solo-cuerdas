package ar.solocuerdas.backend.listings;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoints navegables sin cuenta. Viven bajo /api/public/** (la unica zona
// abierta en SecurityConfig) y en un controller aparte del autenticado, para
// que el limite entre "cualquiera puede ver esto" y "requiere token" sea
// evidente en la estructura del codigo, no solo en la config de seguridad.
@RestController
@RequestMapping("/api/public/listings")
public class PublicListingController {

    private final ListingRepository listingRepository;

    public PublicListingController(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    @GetMapping("/{id}")
    public ListingResponse getById(@PathVariable UUID id) {
        // El filtro por status va en la consulta, no en un if posterior: una
        // publicacion que no esta activa no se puede devolver ni por error.
        Listing listing = listingRepository.findByIdAndStatus(id, "active").orElseThrow();
        return ListingResponse.from(listing);
    }

    @GetMapping
    public List<ListingResponse> list() {
        return listingRepository.findByStatus("active").stream()
                .map(ListingResponse::from)
                .collect(Collectors.toList());
    }
}
