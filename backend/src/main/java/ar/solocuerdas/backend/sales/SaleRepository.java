package ar.solocuerdas.backend.sales;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findByBuyerId(UUID buyerId);

    // sales no guarda el vendedor directo (se obtiene desde listings) -- se
    // usa junto con listingRepository.findBySellerId para armar "mis ventas
    // como vendedor".
    List<Sale> findByListingIdIn(List<UUID> listingIds);

    long countByBuyerIdAndStatus(UUID buyerId, String status);
}
