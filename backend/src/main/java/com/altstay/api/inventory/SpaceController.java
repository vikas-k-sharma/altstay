package com.altstay.api.inventory;

import com.altstay.api.inventory.InventoryService.CreateSpaceRequest;
import com.altstay.api.inventory.InventoryService.SpaceDto;
import com.altstay.api.inventory.InventoryService.UpdateSpaceRequest;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties/{slug}/spaces")
@ConditionalOnProperty(name = "spring.datasource.url")
public class SpaceController {

    private final InventoryService inventoryService;

    public SpaceController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<SpaceDto>> listSpaces(@PathVariable String slug) {
        return ResponseEntity.ok(inventoryService.listSpaces(slug));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpaceDto> getSpace(@PathVariable String slug, @PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.getSpace(slug, id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<SpaceDto> createSpace(
            @PathVariable String slug,
            @Valid @RequestBody CreateSpaceRequest request
    ) {
        SpaceDto created = inventoryService.createSpace(slug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<SpaceDto> updateSpace(
            @PathVariable String slug,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSpaceRequest request
    ) {
        SpaceDto updated = inventoryService.updateSpace(slug, id, request);
        return ResponseEntity.ok(updated);
    }
}
