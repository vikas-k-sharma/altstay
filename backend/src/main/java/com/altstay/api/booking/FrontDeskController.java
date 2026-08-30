package com.altstay.api.booking;

import com.altstay.api.booking.BookingService.FrontDeskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The front desk's day view: arrivals, departures and who is in the house (§9).
 *
 * <p>Open to every staff role. Reading the day's movements is the whole job of the desk, and
 * §8 puts the read side of the booking surface in reach of {@code FRONT_DESK}.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.datasource.url")
public class FrontDeskController {

    private final BookingService bookingService;

    /**
     * @param date optional; defaults to today <em>in the property's timezone</em>, which is the
     *             only definition of "today" a front desk recognises.
     */
    @GetMapping("/api/v1/properties/{slug}/front-desk")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<FrontDeskResponse> getFrontDesk(
            @PathVariable String slug,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(bookingService.getFrontDesk(slug, date));
    }
}
