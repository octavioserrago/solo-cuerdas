package ar.solocuerdas.backend.reports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ar.solocuerdas.backend.conversations.BlockedBuyer;
import ar.solocuerdas.backend.conversations.BlockedBuyerRepository;
import ar.solocuerdas.backend.conversations.Conversation;
import ar.solocuerdas.backend.conversations.ConversationRepository;
import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;
import ar.solocuerdas.backend.users.Profile;
import ar.solocuerdas.backend.users.ProfileRepository;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reportRepository;
    private final ProfileRepository profileRepository;
    private final ConversationRepository conversationRepository;
    private final ListingRepository listingRepository;
    private final BlockedBuyerRepository blockedBuyerRepository;

    public ReportController(
            ReportRepository reportRepository,
            ProfileRepository profileRepository,
            ConversationRepository conversationRepository,
            ListingRepository listingRepository,
            BlockedBuyerRepository blockedBuyerRepository) {
        this.reportRepository = reportRepository;
        this.profileRepository = profileRepository;
        this.conversationRepository = conversationRepository;
        this.listingRepository = listingRepository;
        this.blockedBuyerRepository = blockedBuyerRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateReportRequest request) {
        if (request.listingId() == null && request.reportedProfileId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La denuncia tiene que apuntar a una publicacion o a un perfil.");
        }

        UUID reporterId = UUID.fromString(jwt.getSubject());

        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporterId(reporterId);
        report.setListingId(request.listingId());
        report.setReportedProfileId(request.reportedProfileId());
        report.setConversationId(request.conversationId());
        report.setReason(request.reason());
        report.setStatus("open");
        report.setCreatedAt(Instant.now());

        Report saved = reportRepository.save(report);

        if (request.conversationId() != null && request.reportedProfileId() != null) {
            blockBuyerIfReporterIsSeller(reporterId, request.conversationId(), request.reportedProfileId(), saved.getId());
        }

        return ReportResponse.from(saved);
    }

    // El bloqueo es acotado al vendedor que denuncia, nunca global: solo se
    // dispara si la denuncia nace de una conversacion y quien denuncia es
    // justo el vendedor de esa publicacion (no cualquier denuncia de perfil
    // cuenta). Ademas corta toda conversacion activa que ese comprador tenga
    // con el mismo vendedor en otras publicaciones, no solo esta.
    private void blockBuyerIfReporterIsSeller(UUID reporterId, UUID conversationId, UUID reportedProfileId, UUID reportId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        Listing listing = listingRepository.findById(conversation.getListingId()).orElseThrow();

        boolean reporterIsSeller = listing.getSellerId().equals(reporterId);
        boolean reportedIsTheBuyer = conversation.getBuyerId().equals(reportedProfileId);
        if (!reporterIsSeller || !reportedIsTheBuyer) {
            return;
        }

        UUID sellerId = listing.getSellerId();
        UUID buyerId = reportedProfileId;

        if (!blockedBuyerRepository.existsBySellerIdAndBuyerId(sellerId, buyerId)) {
            BlockedBuyer block = new BlockedBuyer();
            block.setId(UUID.randomUUID());
            block.setSellerId(sellerId);
            block.setBuyerId(buyerId);
            block.setReportId(reportId);
            block.setCreatedAt(Instant.now());
            blockedBuyerRepository.save(block);
        }

        List<UUID> sellerListingIds = listingRepository.findBySellerId(sellerId).stream()
                .map(Listing::getId)
                .collect(Collectors.toList());
        conversationRepository.findByListingIdInAndBuyerId(sellerListingIds, buyerId).stream()
                .filter(c -> "pending".equals(c.getStatus()) || "accepted".equals(c.getStatus()))
                .forEach(c -> {
                    c.setStatus("rejected");
                    conversationRepository.save(c);
                });
    }

    @GetMapping
    public List<ReportResponse> listOpen(@AuthenticationPrincipal Jwt jwt) {
        requireModerator(jwt);
        return reportRepository.findByStatus("open").stream()
                .map(ReportResponse::from)
                .collect(Collectors.toList());
    }

    @PatchMapping("/{id}")
    public ReportResponse resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody ResolveReportRequest request) {
        requireModerator(jwt);

        Report report = reportRepository.findById(id).orElseThrow();
        if (!"open".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La denuncia ya no esta abierta.");
        }
        if (!"resolved".equals(request.status()) && !"dismissed".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede resolver como resolved o dismissed.");
        }

        report.setStatus(request.status());
        report.setResolvedBy(UUID.fromString(jwt.getSubject()));

        Report saved = reportRepository.save(report);

        if (Boolean.TRUE.equals(request.liftBlock())) {
            blockedBuyerRepository.deleteByReportId(saved.getId());
        }

        return ReportResponse.from(saved);
    }

    private void requireModerator(Jwt jwt) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Profile requester = profileRepository.findById(requesterId).orElseThrow();
        if (!"moderator".equals(requester.getRole()) && !"admin".equals(requester.getRole())) {
            throw new AccessDeniedException("Necesitas ser moderador para esta accion.");
        }
    }
}
