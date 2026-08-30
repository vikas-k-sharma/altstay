package com.altstay.api.amenity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/amenities")
@ConditionalOnProperty(name = "spring.datasource.url")
public class AmenityController {

    public record AmenityResponse(String code, String label, String category) {
        public static AmenityResponse from(Amenity a) {
            return new AmenityResponse(a.getCode(), a.getLabel(), a.getCategory());
        }
    }

    private final AmenityRepository amenityRepository;

    public AmenityController(AmenityRepository amenityRepository) {
        this.amenityRepository = amenityRepository;
    }

    @GetMapping
    public ResponseEntity<List<AmenityResponse>> listAmenities() {
        List<AmenityResponse> amenities = amenityRepository.findAllByOrderByCategoryAscCodeAsc()
                .stream()
                .map(AmenityResponse::from)
                .toList();
        return ResponseEntity.ok(amenities);
    }
}
