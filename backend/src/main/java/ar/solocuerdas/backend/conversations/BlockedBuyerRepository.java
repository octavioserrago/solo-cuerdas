package ar.solocuerdas.backend.conversations;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedBuyerRepository extends JpaRepository<BlockedBuyer, UUID> {

    boolean existsBySellerIdAndBuyerId(UUID sellerId, UUID buyerId);

    void deleteByReportId(UUID reportId);
}
