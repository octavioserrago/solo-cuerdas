package ar.solocuerdas.backend.reports;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ar.solocuerdas.backend.users.Profile;
import ar.solocuerdas.backend.users.ProfileRepository;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reportRepository;
    private final ProfileRepository profileRepository;

    public ReportController(ReportRepository reportRepository, ProfileRepository profileRepository) {
        this.reportRepository = reportRepository;
        this.profileRepository = profileRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateReportRequest request) {
        if (request.listingId() == null && request.reportedProfileId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La denuncia tiene que apuntar a una publicacion o a un perfil.");
        }

        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporterId(UUID.fromString(jwt.getSubject()));
        report.setListingId(request.listingId());
        report.setReportedProfileId(request.reportedProfileId());
        report.setReason(request.reason());
        report.setStatus("open");
        report.setCreatedAt(Instant.now());

        Report saved = reportRepository.save(report);
        return ReportResponse.from(saved);
    }

    @PatchMapping("/{id}")
    public ReportResponse resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody ResolveReportRequest request) {
        UUID requesterId = UUID.fromString(jwt.getSubject());
        Profile requester = profileRepository.findById(requesterId).orElseThrow();

        if (!"moderator".equals(requester.getRole()) && !"admin".equals(requester.getRole())) {
            throw new AccessDeniedException("Necesitas ser moderador para resolver una denuncia.");
        }

        Report report = reportRepository.findById(id).orElseThrow();
        if (!"open".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La denuncia ya no esta abierta.");
        }
        if (!"resolved".equals(request.status()) && !"dismissed".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede resolver como resolved o dismissed.");
        }

        report.setStatus(request.status());
        report.setResolvedBy(requesterId);

        Report saved = reportRepository.save(report);
        return ReportResponse.from(saved);
    }
}
