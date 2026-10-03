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
import java.util.List;
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
import ar.solocuerdas.backend.reviews.Review;
import ar.solocuerdas.backend.reviews.ReviewRepository;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileRepository profileRepository;

    @MockitoBean
    private ReviewRepository reviewRepository;

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

    @Test
    void getPublicProfileReturnsOnlyPublicFields() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Profile target = new Profile();
        target.setId(targetId);
        target.setFirstName("Octavio");
        target.setLastName("Serrago");
        target.setUsername("octa");
        target.setPhone("1122334455");
        target.setProvince("Buenos Aires");
        target.setCity("La Plata");
        target.setRole("user");
        target.setIdentityStatus("verified");
        target.setRatingAverage(new BigDecimal("4.50"));
        target.setRatingCount(3);
        target.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(profileRepository.findById(targetId)).thenReturn(Optional.of(target));

        mockMvc.perform(get("/api/users/{id}", targetId)
                        .with(jwt().jwt(j -> j.subject(requesterId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.toString()))
                .andExpect(jsonPath("$.username").value("octa"))
                .andExpect(jsonPath("$.firstName").value("Octavio"))
                .andExpect(jsonPath("$.province").value("Buenos Aires"))
                .andExpect(jsonPath("$.identityStatus").value("verified"))
                .andExpect(jsonPath("$.ratingAverage").value(4.50))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void getPublicProfileReturns404WhenProfileDoesNotExist() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        when(profileRepository.findById(targetId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{id}", targetId)
                        .with(jwt().jwt(j -> j.subject(requesterId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsTheReviewsOfAUser() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setSaleId(UUID.randomUUID());
        review.setReviewerId(UUID.randomUUID());
        review.setRevieweeId(targetId);
        review.setRating((short) 5);
        review.setComment("Excelente, tal cual la descripcion.");

        when(reviewRepository.findByRevieweeId(targetId)).thenReturn(List.of(review));

        mockMvc.perform(get("/api/users/{id}/reviews", targetId)
                        .with(jwt().jwt(j -> j.subject(requesterId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].revieweeId").value(targetId.toString()))
                .andExpect(jsonPath("$[0].rating").value(5));
    }
}
