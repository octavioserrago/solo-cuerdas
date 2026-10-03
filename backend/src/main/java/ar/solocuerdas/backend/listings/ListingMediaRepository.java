package ar.solocuerdas.backend.listings;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingMediaRepository extends JpaRepository<ListingMedia, UUID> {

    // Escopeado a la publicacion a proposito: evita que alguien confirme (o,
    // a futuro, dispare el borrado de Storage de) una media de OTRA
    // publicacion, aunque sea dueno de la publicacion que puso en la URL.
    Optional<ListingMedia> findByIdAndListingId(UUID id, UUID listingId);
}
