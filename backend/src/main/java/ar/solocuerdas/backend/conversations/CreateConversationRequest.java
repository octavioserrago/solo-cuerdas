package ar.solocuerdas.backend.conversations;

import java.util.UUID;

public record CreateConversationRequest(UUID listingId, String contactReason) {
}
