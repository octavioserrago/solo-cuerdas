package ar.solocuerdas.backend.conversations;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID listingId,
        UUID buyerId,
        String status,
        String contactReason,
        Instant createdAt,
        BuyerSummary buyer) {

    static ConversationResponse from(Conversation conversation, BuyerSummary buyer) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getListingId(),
                conversation.getBuyerId(),
                conversation.getStatus(),
                conversation.getContactReason(),
                conversation.getCreatedAt(),
                buyer);
    }
}
