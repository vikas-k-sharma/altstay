package com.altstay.api.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped property endpoints.
 */
@RestController
@RequestMapping("/api/v1/properties")
@ConditionalOnProperty(name = "spring.datasource.url")
public class PropertyController {

    public record PropertyResponse(
            UUID id,
            UUID tenantId,
            String name,
            String slug,
            String legalName,
            String description,
            String status,
            String timezone,
            String currencyCode,
            String countryCode,
            String addressLine1,
            String addressLine2,
            String city,
            String stateRegion,
            String postalCode,
            String contactEmail,
            String contactPhone,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            Integer taxRateBps,
            List<String> amenities,
            OffsetDateTime createdAt
    ) {
        public static PropertyResponse from(Property p, List<String> amenities) {
            return new PropertyResponse(
                    p.getId(),
                    p.getTenantId(),
                    p.getName(),
                    p.getSlug(),
                    p.getLegalName(),
                    p.getDescription(),
                    p.getStatus(),
                    p.getTimezone(),
                    p.getCurrencyCode(),
                    p.getCountryCode(),
                    p.getAddressLine1(),
                    p.getAddressLine2(),
                    p.getCity(),
                    p.getStateRegion(),
                    p.getPostalCode(),
                    p.getContactEmail(),
                    p.getContactPhone(),
                    p.getCheckInTime(),
                    p.getCheckOutTime(),
                    p.getTaxRateBps(),
                    amenities,
                    p.getCreatedAt()
            );
        }
    }

    public record CreatePropertyRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "slug is required") String slug,
            String legalName,
            String description,
            String status,
            String timezone,
            String currencyCode,
            String countryCode,
            String addressLine1,
            String addressLine2,
            String city,
            String stateRegion,
            String postalCode,
            String contactEmail,
            String contactPhone,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            @Min(0) @Max(10000) Integer taxRateBps,
            List<String> amenities
    ) {}

    public record UpdatePropertyRequest(
            String name,
            String legalName,
            String description,
            String status,
            String timezone,
            String currencyCode,
            String countryCode,
            String addressLine1,
            String addressLine2,
            String city,
            String stateRegion,
            String postalCode,
            String contactEmail,
            String contactPhone,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            @Min(0) @Max(10000) Integer taxRateBps,
            List<String> amenities
    ) {}

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyResponse>> listProperties() {
        List<PropertyResponse> properties = propertyService.listProperties()
                .stream()
                .map(p -> PropertyResponse.from(p, propertyService.getPropertyAmenities(p.getId())))
                .toList();
        return ResponseEntity.ok(properties);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PropertyResponse> getPropertyBySlug(@PathVariable String slug) {
        return propertyService.getPropertyBySlug(slug)
                .map(p -> PropertyResponse.from(p, propertyService.getPropertyAmenities(p.getId())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PropertyResponse> createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        Property created = propertyService.createProperty(
                request.name(),
                request.slug(),
                request.legalName(),
                request.description(),
                request.status(),
                request.timezone(),
                request.currencyCode(),
                request.countryCode(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.stateRegion(),
                request.postalCode(),
                request.contactEmail(),
                request.contactPhone(),
                request.checkInTime(),
                request.checkOutTime(),
                request.taxRateBps(),
                request.amenities()
        );
        List<String> amenities = propertyService.getPropertyAmenities(created.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PropertyResponse.from(created, amenities));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PropertyResponse> updateProperty(
            @PathVariable String slug,
            @Valid @RequestBody UpdatePropertyRequest request
    ) {
        Property updated = propertyService.updateProperty(
                slug,
                request.name(),
                request.legalName(),
                request.description(),
                request.status(),
                request.timezone(),
                request.currencyCode(),
                request.countryCode(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.stateRegion(),
                request.postalCode(),
                request.contactEmail(),
                request.contactPhone(),
                request.checkInTime(),
                request.checkOutTime(),
                request.taxRateBps(),
                request.amenities()
        );
        List<String> amenities = propertyService.getPropertyAmenities(updated.getId());
        return ResponseEntity.ok(PropertyResponse.from(updated, amenities));
    }
}
