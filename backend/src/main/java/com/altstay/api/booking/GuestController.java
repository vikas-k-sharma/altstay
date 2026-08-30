package com.altstay.api.booking;

import com.altstay.api.booking.BookingService.GuestDto;
import com.altstay.api.tenancy.CurrentTenantHolder;
import com.altstay.api.tenancy.TenantScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/v1/guests")
@RequiredArgsConstructor
@TenantScoped
@Transactional
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.datasource.url")
public class GuestController {

    private final GuestRepository guestRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<List<GuestDto>> listGuests() {
        List<GuestDto> guests = guestRepository.findAll().stream().map(g -> new GuestDto(
                g.getId(),
                g.getFullName(),
                g.getEmail(),
                g.getPhone(),
                g.getCountryCode(),
                g.getDateOfBirth(),
                g.getNotes()
        )).toList();
        return ResponseEntity.ok(guests);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<GuestDto> getGuest(@PathVariable UUID id) {
        Guest g = guestRepository.findById(id)
                .orElseThrow(() -> new GuestNotFoundException("Guest not found: " + id));
        return ResponseEntity.ok(new GuestDto(
                g.getId(),
                g.getFullName(),
                g.getEmail(),
                g.getPhone(),
                g.getCountryCode(),
                g.getDateOfBirth(),
                g.getNotes()
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<GuestDto> createGuest(@RequestBody GuestDto dto) {
        UUID tenantId = CurrentTenantHolder.get()
                .orElseThrow(() -> new IllegalStateException("No tenant context available"));
        Guest g = new Guest(tenantId, dto.fullName(), dto.email(), dto.phone());
        g.setCountryCode(dto.countryCode());
        g.setDateOfBirth(dto.dateOfBirth());
        g.setNotes(dto.notes());
        g = guestRepository.save(g);
        return ResponseEntity.status(HttpStatus.CREATED).body(new GuestDto(
                g.getId(),
                g.getFullName(),
                g.getEmail(),
                g.getPhone(),
                g.getCountryCode(),
                g.getDateOfBirth(),
                g.getNotes()
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<GuestDto> updateGuest(@PathVariable UUID id, @RequestBody GuestDto dto) {
        Guest g = guestRepository.findById(id)
                .orElseThrow(() -> new GuestNotFoundException("Guest not found: " + id));
        g.setFullName(dto.fullName());
        g.setEmail(dto.email());
        g.setPhone(dto.phone());
        g.setCountryCode(dto.countryCode());
        g.setDateOfBirth(dto.dateOfBirth());
        g.setNotes(dto.notes());
        g = guestRepository.save(g);
        return ResponseEntity.ok(new GuestDto(
                g.getId(),
                g.getFullName(),
                g.getEmail(),
                g.getPhone(),
                g.getCountryCode(),
                g.getDateOfBirth(),
                g.getNotes()
        ));
    }
}
