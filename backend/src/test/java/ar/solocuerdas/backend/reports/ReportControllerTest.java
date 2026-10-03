package ar.solocuerdas.backend.reports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import ar.solocuerdas.backend.conversations.BlockedBuyer;
import ar.solocuerdas.backend.conversations.BlockedBuyerRepository;
import ar.solocuerdas.backend.conversations.Conversation;
import ar.solocuerdas.backend.conversations.ConversationRepository;
import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;
import ar.solocuerdas.backend.users.Profile;
import ar.solocuerdas.backend.users.ProfileRepository;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportRepository reportRepository;

    @MockitoBean
    private ProfileRepository profileRepository;

    @MockitoBean
    private ConversationRepository conversationRepository;

    @MockitoBean
    private ListingRepository listingRepository;

    @MockitoBean
    private BlockedBuyerRepository blockedBuyerRepository;

    @Test
    void reportsAListing() throws Exception {
        UUID reporterId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/reports")
                        .with(jwt().jwt(j -> j.subject(reporterId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\": \"%s\", \"reason\": \"La foto no coincide con el instrumento real.\"}"
                                .formatted(listingId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporterId").value(reporterId.toString()))
                .andExpect(jsonPath("$.listingId").value(listingId.toString()))
                .andExpect(jsonPath("$.status").value("open"));
    }

    @Test
    void reportsAProfile() throws Exception {
        UUID reporterId = UUID.randomUUID();
        UUID reportedProfileId = UUID.randomUUID();

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/reports")
                        .with(jwt().jwt(j -> j.subject(reporterId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedProfileId\": \"%s\", \"reason\": \"Nunca entrego el instrumento acordado.\"}"
                                .formatted(reportedProfileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportedProfileId").value(reportedProfileId.toString()));
    }

    @Test
    void reportingFromAConversationBlocksTheBuyerAndClosesActiveConversations() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setSellerId(sellerId);

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setListingId(listingId);
        conversation.setBuyerId(buyerId);
        conversation.setStatus("accepted");

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(blockedBuyerRepository.existsBySellerIdAndBuyerId(sellerId, buyerId)).thenReturn(false);
        when(listingRepository.findBySellerId(sellerId)).thenReturn(List.of(listing));
        when(conversationRepository.findByListingIdInAndBuyerId(List.of(listingId), buyerId))
                .thenReturn(List.of(conversation));

        mockMvc.perform(post("/api/reports")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedProfileId\": \"%s\", \"conversationId\": \"%s\", \"reason\": \"Propuso una permuta en el chat.\"}"
                                .formatted(buyerId, conversationId)))
                .andExpect(status().isCreated());

        verify(blockedBuyerRepository).save(argThat(
                block -> block.getSellerId().equals(sellerId) && block.getBuyerId().equals(buyerId)));
        verify(conversationRepository).save(argThat(c -> "rejected".equals(c.getStatus())));
    }

    @Test
    void reportingDoesNotBlockWhenReporterIsNotTheSeller() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setSellerId(sellerId);

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setListingId(listingId);
        conversation.setBuyerId(buyerId);
        conversation.setStatus("accepted");

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(post("/api/reports")
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedProfileId\": \"%s\", \"conversationId\": \"%s\", \"reason\": \"No era el vendedor ni el comprador.\"}"
                                .formatted(buyerId, conversationId)))
                .andExpect(status().isCreated());

        verify(blockedBuyerRepository, never()).save(any(BlockedBuyer.class));
    }

    @Test
    void rejectsAReportWithoutAnyTarget() throws Exception {
        UUID reporterId = UUID.randomUUID();

        mockMvc.perform(post("/api/reports")
                        .with(jwt().jwt(j -> j.subject(reporterId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Motivo sin ningun objetivo indicado.\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderatorResolvesAnOpenReport() throws Exception {
        UUID moderatorId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Report report = openReport(reportId);
        Profile moderator = profileWithRole(moderatorId, "moderator");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(profileRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .with(jwt().jwt(j -> j.subject(moderatorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"resolved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.resolvedBy").value(moderatorId.toString()));
    }

    @Test
    void nonModeratorCannotResolveAReport() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Report report = openReport(reportId);
        Profile regularUser = profileWithRole(userId, "user");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(regularUser));

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"resolved\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAStatusValueOutsideResolvedAndDismissed() throws Exception {
        UUID moderatorId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Report report = openReport(reportId);
        Profile moderator = profileWithRole(moderatorId, "moderator");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(profileRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .with(jwt().jwt(j -> j.subject(moderatorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"open\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotResolveAReportThatIsNotOpen() throws Exception {
        UUID moderatorId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Report report = openReport(reportId);
        report.setStatus("dismissed");
        Profile moderator = profileWithRole(moderatorId, "moderator");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(profileRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .with(jwt().jwt(j -> j.subject(moderatorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"resolved\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderatorLiftsTheBlockWhenResolvingAReport() throws Exception {
        UUID moderatorId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Report report = openReport(reportId);
        Profile moderator = profileWithRole(moderatorId, "moderator");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(profileRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .with(jwt().jwt(j -> j.subject(moderatorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"dismissed\", \"liftBlock\": true}"))
                .andExpect(status().isOk());

        verify(blockedBuyerRepository).deleteByReportId(reportId);
    }

    @Test
    void moderatorResolvingWithoutLiftBlockKeepsTheBlock() throws Exception {
        UUID moderatorId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Report report = openReport(reportId);
        Profile moderator = profileWithRole(moderatorId, "moderator");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(profileRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .with(jwt().jwt(j -> j.subject(moderatorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"resolved\"}"))
                .andExpect(status().isOk());

        verify(blockedBuyerRepository, never()).deleteByReportId(any(UUID.class));
    }

    @Test
    void moderatorListsOpenReports() throws Exception {
        UUID moderatorId = UUID.randomUUID();
        Profile moderator = profileWithRole(moderatorId, "moderator");
        Report report = openReport(UUID.randomUUID());

        when(profileRepository.findById(moderatorId)).thenReturn(Optional.of(moderator));
        when(reportRepository.findByStatus("open")).thenReturn(List.of(report));

        mockMvc.perform(get("/api/reports")
                        .with(jwt().jwt(j -> j.subject(moderatorId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("open"));
    }

    @Test
    void nonModeratorCannotListReports() throws Exception {
        UUID userId = UUID.randomUUID();
        Profile regularUser = profileWithRole(userId, "user");

        when(profileRepository.findById(userId)).thenReturn(Optional.of(regularUser));

        mockMvc.perform(get("/api/reports")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isForbidden());
    }

    private Report openReport(UUID id) {
        Report report = new Report();
        report.setId(id);
        report.setReporterId(UUID.randomUUID());
        report.setReportedProfileId(UUID.randomUUID());
        report.setReason("Nunca entrego el instrumento acordado.");
        report.setStatus("open");
        return report;
    }

    private Profile profileWithRole(UUID id, String role) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setRole(role);
        return profile;
    }
}
