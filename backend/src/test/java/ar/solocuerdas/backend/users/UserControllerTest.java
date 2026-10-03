package ar.solocuerdas.backend.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.solocuerdas.backend.config.SecurityConfig;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileRepository profileRepository;

    @Test
    void getMeReturnsProfileDataForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();

        Profile profile = new Profile();
        profile.setId(userId);
        profile.setFirstName("Octavio");
        profile.setLastName("Serrago");
        profile.setUsername("octa");
        profile.setPhone("1122334455");
        profile.setProvince("Buenos Aires");
        profile.setCity("La Plata");
        profile.setRole("user");
        profile.setIdentityStatus("unverified");
        profile.setRatingAverage(new BigDecimal("4.50"));
        profile.setRatingCount(3);
        profile.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/api/users/me")
                .with(jwt().jwt(j -> j.subject(userId.toString())
                        .claim("email", "octa@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("octa@example.com"))
                .andExpect(jsonPath("$.firstName").value("Octavio"))
                .andExpect(jsonPath("$.lastName").value("Serrago"))
                .andExpect(jsonPath("$.username").value("octa"))
                .andExpect(jsonPath("$.province").value("Buenos Aires"))
                .andExpect(jsonPath("$.city").value("La Plata"))
                .andExpect(jsonPath("$.identityStatus").value("unverified"))
                .andExpect(jsonPath("$.ratingAverage").value(4.50))
                .andExpect(jsonPath("$.ratingCount").value(3));
    }

    @Test
    void patchMeUpdatesOnlyTheProvidedFields() throws Exception {
        UUID userId = UUID.randomUUID();

        Profile existing = new Profile();
        existing.setId(userId);
        existing.setFirstName("Octavio");
        existing.setLastName("Serrago");
        existing.setUsername("octa");
        existing.setPhone("1122334455");
        existing.setProvince("Buenos Aires");
        existing.setCity("La Plata");
        existing.setRole("user");
        existing.setIdentityStatus("unverified");
        existing.setRatingAverage(BigDecimal.ZERO);
        existing.setRatingCount(0);
        existing.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/users/me")
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\": \"Mar del Plata\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Mar del Plata"))
                .andExpect(jsonPath("$.province").value("Buenos Aires"))
                .andExpect(jsonPath("$.username").value("octa"))
                .andExpect(jsonPath("$.firstName").value("Octavio"));
    }

    @Test
    void patchMeRejectsAnInvalidUsername() throws Exception {
        UUID userId = UUID.randomUUID();

        Profile existing = new Profile();
        existing.setId(userId);
        existing.setUsername("octa");

        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/users/me")
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"a\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchMeRejectsADuplicateUsername() throws Exception {
        UUID userId = UUID.randomUUID();

        Profile existing = new Profile();
        existing.setId(userId);
        existing.setUsername("octa");

        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_profiles_username\""));

        mockMvc.perform(patch("/api/users/me")
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ya_existente\"}"))
                .andExpect(status().isConflict());
    }
}
