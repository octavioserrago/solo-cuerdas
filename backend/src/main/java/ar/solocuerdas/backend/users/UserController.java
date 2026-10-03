package ar.solocuerdas.backend.users;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProfileRepository profileRepository;

    public UserController(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        Profile profile = profileRepository.findById(id).orElseThrow();
        return ProfileResponse.from(profile, jwt.getClaimAsString("email"));
    }

    @PatchMapping("/me")
    public ProfileResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        UUID id = UUID.fromString(jwt.getSubject());
        Profile profile = profileRepository.findById(id).orElseThrow();

        if (request.firstName() != null) {
            profile.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            profile.setLastName(request.lastName());
        }
        if (request.username() != null) {
            profile.setUsername(request.username());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone());
        }
        if (request.province() != null) {
            profile.setProvince(request.province());
        }
        if (request.city() != null) {
            profile.setCity(request.city());
        }

        Profile saved = profileRepository.save(profile);
        return ProfileResponse.from(saved, jwt.getClaimAsString("email"));
    }
}
