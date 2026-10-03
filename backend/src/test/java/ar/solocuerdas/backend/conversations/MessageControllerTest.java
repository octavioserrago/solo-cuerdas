package ar.solocuerdas.backend.conversations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.solocuerdas.backend.config.SecurityConfig;
import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;

@WebMvcTest(MessageController.class)
@Import(SecurityConfig.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageRepository messageRepository;

    @MockitoBean
    private ConversationRepository conversationRepository;

    @MockitoBean
    private ListingRepository listingRepository;

    @Test
    void buyerSendsFirstMessageInAnAcceptedConversation() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));
        when(messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversationId)).thenReturn(Optional.empty());
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Hola, me interesa el instrumento.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(buyerId.toString()))
                .andExpect(jsonPath("$.content").value("Hola, me interesa el instrumento."));
    }

    @Test
    void sellerSendsFirstMessageInAnAcceptedConversation() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));
        when(messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversationId)).thenReturn(Optional.empty());
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Hola! Si, sigue disponible.\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void theSameSenderCannotSendTwoMessagesInARow() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Message lastMessage = message(UUID.randomUUID(), conversationId, buyerId, "Hola");

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));
        when(messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversationId))
                .thenReturn(Optional.of(lastMessage));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Otra pregunta mas.\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void theOtherParticipantCanReplyAfterTheLastMessage() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Message lastMessage = message(UUID.randomUUID(), conversationId, buyerId, "Hola");

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));
        when(messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversationId))
                .thenReturn(Optional.of(lastMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Si, todavia esta disponible.\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void someoneWhoIsNotPartOfTheConversationCannotSendMessages() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Hola.\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotSendMessagesWhenTheConversationIsNotAccepted() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "pending")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Hola.\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankMessageIsRejected() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageOverTheCharacterLimitIsRejected() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String tooLong = "a".repeat(301);

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"%s\"}".formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageWithABannedWordIsRejected() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Te cambio esto por un ARMA, interesa?\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void innocentWordContainingABannedSubstringIsAllowed() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(conversationId, listingId, buyerId, "accepted")));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, sellerId)));
        when(messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversationId)).thenReturn(Optional.empty());
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Lo guardo en el armario de casa.\"}"))
                .andExpect(status().isCreated());
    }

    private Conversation conversation(UUID id, UUID listingId, UUID buyerId, String status) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setListingId(listingId);
        conversation.setBuyerId(buyerId);
        conversation.setStatus(status);
        conversation.setContactReason("consulta");
        return conversation;
    }

    private Listing listing(UUID id, UUID sellerId) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setStatus("active");
        return listing;
    }

    private Message message(UUID id, UUID conversationId, UUID senderId, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setIsRead(false);
        message.setSentAt(Instant.parse("2026-01-01T00:00:00Z"));
        return message;
    }
}
