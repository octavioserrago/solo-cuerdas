package ar.solocuerdas.backend.listings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    long countBySellerIdAndStatus(UUID sellerId, String status);

    Optional<Listing> findByIdAndStatus(UUID id, String status);

    List<Listing> findByStatus(String status);

    List<Listing> findBySellerId(UUID sellerId);
}
