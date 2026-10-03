package ar.solocuerdas.backend.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import ar.solocuerdas.backend.listings.Listing;
import ar.solocuerdas.backend.listings.ListingRepository;

@WebMvcTest(SaleController.class)
@Import(SecurityConfig.class)
class SaleControllerTest {

    private static final String VALID_CREATE_BODY = """
            {
              "listingId": "%s",
              "buyerId": "%s",
              "finalPrice": 140000,
              "currency": "ARS"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SaleRepository saleRepository;

    @MockitoBean
    private ListingRepository listingRepository;

    @Test
    void sellerCreatesASaleForTheirOwnActiveListing() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = activeListing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/sales")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.formatted(listingId, buyerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value(listingId.toString()))
                .andExpect(jsonPath("$.buyerId").value(buyerId.toString()))
                .andExpect(jsonPath("$.status").value("pending_confirmation"))
                .andExpect(jsonPath("$.confirmationCode").isString());
    }

    @Test
    void nonSellerCannotCreateASaleForSomeoneElsesListing() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = activeListing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(post("/api/sales")
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.formatted(listingId, buyerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotCreateASaleForAListingThatIsNotActive() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = activeListing(listingId, sellerId);
        listing.setStatus("paused");

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        mockMvc.perform(post("/api/sales")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.formatted(listingId, buyerId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsADuplicateSaleForTheSameListing() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = activeListing(listingId, sellerId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(saleRepository.save(any(Sale.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_sales_pending\""));

        mockMvc.perform(post("/api/sales")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY.formatted(listingId, buyerId)))
                .andExpect(status().isConflict());
    }

    @Test
    void buyerConfirmsWithTheCorrectCode() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        Sale sale = pendingSale(saleId, listingId, buyerId, "123456");
        Listing listing = activeListing(listingId, UUID.randomUUID());

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/sales/{id}/confirm", saleId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.confirmationCode").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void confirmingWithTheWrongCodeFails() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        Sale sale = pendingSale(saleId, listingId, buyerId, "123456");

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        mockMvc.perform(post("/api/sales/{id}/confirm", saleId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\": \"000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonBuyerCannotConfirmASale() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        Sale sale = pendingSale(saleId, listingId, buyerId, "123456");

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        mockMvc.perform(post("/api/sales/{id}/confirm", saleId)
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\": \"123456\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotConfirmASaleThatIsNotPending() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        Sale sale = pendingSale(saleId, listingId, buyerId, null);
        sale.setStatus("cancelled");

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        mockMvc.perform(post("/api/sales/{id}/confirm", saleId)
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationCode\": \"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    private Listing activeListing(UUID id, UUID sellerId) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setStatus("active");
        return listing;
    }

    private Sale pendingSale(UUID id, UUID listingId, UUID buyerId, String code) {
        Sale sale = new Sale();
        sale.setId(id);
        sale.setListingId(listingId);
        sale.setBuyerId(buyerId);
        sale.setFinalPrice(new BigDecimal("140000"));
        sale.setCurrency("ARS");
        sale.setStatus("pending_confirmation");
        sale.setConfirmationCode(code);
        return sale;
    }
}
