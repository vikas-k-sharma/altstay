package com.altstay.api.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped property endpoints.
 *
 * <p>Gated on {@code spring.datasource.url} for the same reason {@link PropertyService} is: without
 * a database there is nothing here to serve. The alternative — an optional dependency and a null
 * branch returning an empty list — is test scaffolding living in production code, and it would turn
 * a misconfigured deployment into a silent "you have no properties" rather than a startup failure.
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
            OffsetDateTime createdAt
    ) {
        public static PropertyResponse from(Property property) {
            return new PropertyResponse(
                    property.getId(),
                    property.getTenantId(),
                    property.getName(),
                    property.getSlug(),
                    property.getCreatedAt()
            );
        }
    }

    public record CreatePropertyRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "slug is required") String slug
    ) {}

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyResponse>> listProperties() {
        List<PropertyResponse> properties = propertyService.listProperties()
                .stream()
                .map(PropertyResponse::from)
                .toList();
        return ResponseEntity.ok(properties);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PropertyResponse> getPropertyBySlug(@PathVariable String slug) {
        return propertyService.getPropertyBySlug(slug)
                .map(PropertyResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PropertyResponse> createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        Property created = propertyService.createProperty(request.name(), request.slug());
        return ResponseEntity.status(HttpStatus.CREATED).body(PropertyResponse.from(created));
    }
}
