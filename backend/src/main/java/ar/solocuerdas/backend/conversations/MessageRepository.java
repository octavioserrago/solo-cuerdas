package ar.solocuerdas.backend.conversations;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    // Para saber de quien es el turno: si el ultimo mensaje de la
    // conversacion lo mando el mismo que quiere escribir de nuevo, todavia
    // no le toca.
    Optional<Message> findFirstByConversationIdOrderBySentAtDesc(UUID conversationId);
}
