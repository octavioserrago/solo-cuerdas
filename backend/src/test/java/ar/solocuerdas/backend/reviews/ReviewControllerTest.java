package ar.solocuerdas.backend.reviews;

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
import ar.solocuerdas.backend.sales.Sale;
import ar.solocuerdas.backend.sales.SaleRepository;

@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewControllerTest {

    private static final String BODY = """
            {
              "saleId": "%s",
              "rating": 5,
              "comment": "Todo perfecto, instrumento como se describia."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewRepository reviewRepository;

    @MockitoBean
    private SaleRepository saleRepository;

    @MockitoBean
    private ListingRepository listingRepository;

    @Test
    void buyerReviewsTheSeller() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(completedSale(saleId, listingId, buyerId)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(listingId, sellerId)));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(saleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saleId").value(saleId.toString()))
                .andExpect(jsonPath("$.reviewerId").value(buyerId.toString()))
                .andExpect(jsonPath("$.revieweeId").value(sellerId.toString()))
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void sellerReviewsTheBuyer() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(completedSale(saleId, listingId, buyerId)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(listingId, sellerId)));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject(sellerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(saleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.revieweeId").value(buyerId.toString()));
    }

    @Test
    void cannotReviewASaleThatIsNotCompleted() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Sale sale = completedSale(saleId, listingId, buyerId);
        sale.setStatus("pending_confirmation");

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(saleId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void someoneNotPartOfTheSaleCannotReview() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(completedSale(saleId, listingId, buyerId)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(listingId, sellerId)));

        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject(someoneElseId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(saleId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsADuplicateReviewForTheSameSale() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(completedSale(saleId, listingId, buyerId)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(listingId, sellerId)));
        when(reviewRepository.save(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_reviews_sale_reviewer\""));

        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject(buyerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(saleId)))
                .andExpect(status().isConflict());
    }

    private Sale completedSale(UUID id, UUID listingId, UUID buyerId) {
        Sale sale = new Sale();
        sale.setId(id);
        sale.setListingId(listingId);
        sale.setBuyerId(buyerId);
        sale.setFinalPrice(new BigDecimal("140000"));
        sale.setCurrency("ARS");
        sale.setStatus("completed");
        return sale;
    }

    private Listing listingOf(UUID id, UUID sellerId) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setStatus("sold");
        return listing;
    }
}
