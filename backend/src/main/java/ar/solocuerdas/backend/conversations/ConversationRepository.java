package ar.solocuerdas.backend.conversations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByListingIdAndBuyerId(UUID listingId, UUID buyerId);

    List<Conversation> findByBuyerId(UUID buyerId);

    // conversations no guarda el vendedor directo (se obtiene desde listings) --
    // se usa junto con listingRepository.findBySellerId para armar "mis
    // solicitudes como vendedor", igual que ya se hace en sales.
    List<Conversation> findByListingIdIn(List<UUID> listingIds);

    // Para cerrar de una todas las conversaciones activas de un comprador
    // bloqueado, en cualquiera de las publicaciones del vendedor.
    List<Conversation> findByListingIdInAndBuyerId(List<UUID> listingIds, UUID buyerId);
}
