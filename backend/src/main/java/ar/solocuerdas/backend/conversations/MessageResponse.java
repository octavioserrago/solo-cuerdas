package ar.solocuerdas.backend.conversations;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        Boolean isRead,
        Instant sentAt) {

    static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getContent(),
                message.getIsRead(),
                message.getSentAt());
    }
}
