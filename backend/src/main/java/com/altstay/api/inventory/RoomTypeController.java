package com.altstay.api.inventory;

import com.altstay.api.inventory.InventoryService.CreateRoomTypeRequest;
import com.altstay.api.inventory.InventoryService.RoomTypeDto;
import com.altstay.api.inventory.InventoryService.UpdateRoomTypeRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@ConditionalOnProperty(name = "spring.datasource.url")
public class RoomTypeController {

    private final InventoryService inventoryService;

    public RoomTypeController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/api/v1/properties/{slug}/room-types")
    public ResponseEntity<List<RoomTypeDto>> listRoomTypes(@PathVariable String slug) {
        return ResponseEntity.ok(inventoryService.listRoomTypes(slug));
    }

    @GetMapping("/api/v1/properties/{slug}/room-types/{id}")
    public ResponseEntity<RoomTypeDto> getRoomType(@PathVariable String slug, @PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.getRoomType(slug, id));
    }

    @PostMapping("/api/v1/properties/{slug}/room-types")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<RoomTypeDto> createRoomType(
            @PathVariable String slug,
            @Valid @RequestBody CreateRoomTypeRequest request
    ) {
        RoomTypeDto created = inventoryService.createRoomType(slug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/v1/properties/{slug}/room-types/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<RoomTypeDto> updateRoomType(
            @PathVariable String slug,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomTypeRequest request
    ) {
        RoomTypeDto updated = inventoryService.updateRoomType(slug, id, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/api/v1/room-types/{id}/spaces/{spaceId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Void> associateSpace(@PathVariable UUID id, @PathVariable UUID spaceId) {
        inventoryService.associateSpace(id, spaceId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/api/v1/room-types/{id}/spaces/{spaceId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Void> dissociateSpace(@PathVariable UUID id, @PathVariable UUID spaceId) {
        inventoryService.dissociateSpace(id, spaceId);
        return ResponseEntity.noContent().build();
    }
}
