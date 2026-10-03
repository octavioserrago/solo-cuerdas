package ar.solocuerdas.backend.conversations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    // Friccion anti-spam, no una ficha tecnica -- mismo orden de magnitud
    // que el limite de un comentario de review.
    private static final int MAX_CONTENT_LENGTH = 300;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ListingRepository listingRepository;

    public MessageController(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ListingRepository listingRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.listingRepository = listingRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestBody SendMessageRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        Listing listing = listingRepository.findById(conversation.getListingId()).orElseThrow();

        boolean isBuyer = conversation.getBuyerId().equals(requesterId);
        boolean isSeller = listing.getSellerId().equals(requesterId);
        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("No sos parte de esta conversacion.");
        }
        if (!"accepted".equals(conversation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La conversacion todavia no esta aceptada.");
        }

        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje no puede estar vacio.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mensaje supera el limite de " + MAX_CONTENT_LENGTH + " caracteres.");
        }
        if (BannedWordsFilter.containsBannedWord(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje contiene contenido no permitido.");
        }

        Optional<Message> lastMessage = messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversationId);
        if (lastMessage.isPresent() && lastMessage.get().getSenderId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esperá la respuesta del otro antes de mandar otro mensaje.");
        }

        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversationId);
        message.setSenderId(requesterId);
        message.setContent(content);
        message.setIsRead(false);
        message.setSentAt(Instant.now());

        Message saved = messageRepository.save(message);
        return MessageResponse.from(saved);
    }
}
