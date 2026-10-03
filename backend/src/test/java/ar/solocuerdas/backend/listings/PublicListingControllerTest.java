package ar.solocuerdas.backend.listings;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.solocuerdas.backend.config.SecurityConfig;

@WebMvcTest(PublicListingController.class)
@Import(SecurityConfig.class)
class PublicListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingRepository listingRepository;

    @Test
    void getActiveListingWithoutAuthentication() throws Exception {
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setSellerId(sellerId);
        listing.setCategoryId(1);
        listing.setTitle("Guitarra electrica Fender");
        listing.setSerialNumber("MX21045678");
        listing.setItemCondition("excellent");
        listing.setPrice(new BigDecimal("150000.00"));
        listing.setCurrency("ARS");
        listing.setProvince("Buenos Aires");
        listing.setCity("La Plata");
        listing.setStatus("active");
        listing.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(listingRepository.findByIdAndStatus(listingId, "active")).thenReturn(Optional.of(listing));

        mockMvc.perform(get("/api/public/listings/{id}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(listingId.toString()))
                .andExpect(jsonPath("$.sellerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.title").value("Guitarra electrica Fender"))
                .andExpect(jsonPath("$.serialNumber").value("MX21045678"))
                .andExpect(jsonPath("$.price").value(150000.00));
    }

    @Test
    void getListingThatIsNotActiveReturns404() throws Exception {
        UUID listingId = UUID.randomUUID();

        // Existe, pero esta pausada: el endpoint publico no debe revelarla
        // (ni su contenido, ni que exista).
        when(listingRepository.findByIdAndStatus(listingId, "active")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/listings/{id}", listingId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listActiveListings() throws Exception {
        Listing first = new Listing();
        first.setId(UUID.randomUUID());
        first.setTitle("Guitarra electrica Fender");
        first.setStatus("active");

        Listing second = new Listing();
        second.setId(UUID.randomUUID());
        second.setTitle("Bajo acustico Yamaha");
        second.setStatus("active");

        when(listingRepository.findByStatus("active")).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/public/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Guitarra electrica Fender"))
                .andExpect(jsonPath("$[1].title").value("Bajo acustico Yamaha"));
    }

    @Test
    void listActiveListingsReturnsEmptyArrayWhenNone() throws Exception {
        when(listingRepository.findByStatus("active")).thenReturn(List.of());

        mockMvc.perform(get("/api/public/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
