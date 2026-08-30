package com.altstay.api.amenity;

import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.config.SecurityConfig;
import com.altstay.api.tenancy.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AmenityController.class, properties = "spring.datasource.url=jdbc:postgresql://slice-test/none")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TenantContextFilter.class})
class AmenityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AmenityRepository amenityRepository;

    @Test
    @DisplayName("GET /api/v1/amenities when unauthenticated returns 401 Unauthorized")
    void unauthenticatedAmenitiesReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/amenities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/amenities when authenticated returns 200 OK with vocabulary list")
    void authenticatedUserCanListAmenities() throws Exception {
        when(amenityRepository.findAllByOrderByCategoryAscCodeAsc())
                .thenReturn(List.of(
                        new Amenity("WIFI", "Wi-Fi", "CONNECTIVITY"),
                        new Amenity("BREAKFAST", "Breakfast Included", "FOOD")
                ));

        mockMvc.perform(get("/api/v1/amenities")
                        .with(user("staff@sunset.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("WIFI"))
                .andExpect(jsonPath("$[0].label").value("Wi-Fi"))
                .andExpect(jsonPath("$[0].category").value("CONNECTIVITY"))
                .andExpect(jsonPath("$[1].code").value("BREAKFAST"));
    }
}
