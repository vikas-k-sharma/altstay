package com.altstay.api.inventory;

import com.altstay.api.inventory.AvailabilityService.PropertyAvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.datasource.url")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/api/v1/properties/{slug}/availability")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<PropertyAvailabilityResponse> getAvailability(
            @PathVariable String slug,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID roomTypeId
    ) {
        PropertyAvailabilityResponse res = availabilityService.getAvailability(slug, from, to, roomTypeId);
        return ResponseEntity.ok(res);
    }
}
