package ar.solocuerdas.backend.listings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    long countBySellerIdAndStatus(UUID sellerId, String status);

    Optional<Listing> findByIdAndStatus(UUID id, String status);

    List<Listing> findBySellerId(UUID sellerId);

    // Cada filtro es opcional: si no se manda, esa condicion no excluye nada.
    // Con el volumen de datos de un MVP esto alcanza; si el catalogo crece
    // mucho, conviene revisar si Postgres sigue usando bien los indices
    // parciales (ix_listings_active_category/brand/location) con este patron.
    @Query("""
            select l from Listing l
            where l.status = 'active'
              and (:categoryId is null or l.categoryId = :categoryId)
              and (:brandId is null or l.brandId = :brandId)
              and (:province is null or l.province = :province)
              and (:city is null or l.city = :city)
              and (:minPrice is null or l.price >= :minPrice)
              and (:maxPrice is null or l.price <= :maxPrice)
            """)
    List<Listing> searchActive(
            @Param("categoryId") Integer categoryId,
            @Param("brandId") Integer brandId,
            @Param("province") String province,
            @Param("city") String city,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice);
}
