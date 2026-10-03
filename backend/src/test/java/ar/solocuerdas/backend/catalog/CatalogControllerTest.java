package ar.solocuerdas.backend.catalog;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.solocuerdas.backend.config.SecurityConfig;

@WebMvcTest(CatalogController.class)
@Import(SecurityConfig.class)
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private BrandRepository brandRepository;

    @Test
    void listsCategoriesWithoutAuthentication() throws Exception {
        Category guitar = new Category();
        guitar.setId(1);
        guitar.setName("Guitarra eléctrica");

        when(categoryRepository.findAll()).thenReturn(List.of(guitar));

        mockMvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Guitarra eléctrica"));
    }

    @Test
    void listsBrandsWithoutAuthentication() throws Exception {
        Brand fender = new Brand();
        fender.setId(1);
        fender.setName("Fender");

        when(brandRepository.findAll()).thenReturn(List.of(fender));

        mockMvc.perform(get("/api/public/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Fender"));
    }
}
