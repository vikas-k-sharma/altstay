package com.altstay.api.booking;

import com.altstay.api.auth.TenantUserDetails;
import com.altstay.api.booking.BookingService.BookingResponse;
import com.altstay.api.booking.BookingService.CreateBookingRequest;
import com.altstay.api.booking.BookingService.ModifyBookingRequest;
import com.altstay.api.booking.BookingService.TransitionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.datasource.url")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<BookingResponse> createBooking(
            @RequestBody CreateBookingRequest req,
            @AuthenticationPrincipal TenantUserDetails principal
    ) {
        UUID actingUserId = principal != null ? principal.getUserId() : null;
        BookingResponse created = bookingService.createBooking(req, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<List<BookingResponse>> listBookings(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) UUID guestId,
            @RequestParam(required = false) String reference
    ) {
        List<BookingResponse> list = bookingService.listBookings(propertyId, status, from, to, guestId, reference);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{reference}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable String reference) {
        BookingResponse res = bookingService.getBooking(reference);
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{reference}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<BookingResponse> modifyBooking(
            @PathVariable String reference,
            @RequestBody ModifyBookingRequest req,
            @AuthenticationPrincipal TenantUserDetails principal
    ) {
        UUID actingUserId = principal != null ? principal.getUserId() : null;
        BookingResponse modified = bookingService.modifyBooking(reference, req, actingUserId);
        return ResponseEntity.ok(modified);
    }

    @PostMapping("/{reference}/transitions")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<BookingResponse> transitionBooking(
            @PathVariable String reference,
            @RequestBody TransitionRequest req,
            @AuthenticationPrincipal TenantUserDetails principal
    ) {
        UUID actingUserId = principal != null ? principal.getUserId() : null;
        BookingResponse transitioned = bookingService.transitionBooking(reference, req, actingUserId);
        return ResponseEntity.ok(transitioned);
    }
}
