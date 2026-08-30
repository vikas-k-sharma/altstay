package com.altstay.api.rate;

import com.altstay.api.rate.RateService.CreateRatePlanRequest;
import com.altstay.api.rate.RateService.QuoteRequest;
import com.altstay.api.rate.RateService.QuoteResponse;
import com.altstay.api.rate.RateService.RateCalendarDto;
import com.altstay.api.rate.RateService.RatePlanDto;
import com.altstay.api.rate.RateService.SetRateCalendarRequest;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.datasource.url")
public class RatePlanController {

    private final RateService rateService;

    @GetMapping("/api/v1/properties/{slug}/rate-plans")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<List<RatePlanDto>> listRatePlans(@PathVariable String slug) {
        List<RatePlanDto> list = rateService.listRatePlans(slug);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/api/v1/properties/{slug}/rate-plans")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<RatePlanDto> createRatePlan(
            @PathVariable String slug,
            @RequestBody CreateRatePlanRequest req
    ) {
        RatePlanDto created = rateService.createRatePlan(slug, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/api/v1/rate-plans/{id}/calendar")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<List<RateCalendarDto>> getCalendar(
            @PathVariable UUID id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        List<RateCalendarDto> list = rateService.getCalendar(id, from, to);
        return ResponseEntity.ok(list);
    }

    @PutMapping("/api/v1/rate-plans/{id}/calendar")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Void> setCalendarRange(
            @PathVariable UUID id,
            @RequestBody SetRateCalendarRequest req
    ) {
        rateService.setCalendarRange(id, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/bookings/quote")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'FRONT_DESK')")
    public ResponseEntity<QuoteResponse> getQuote(@RequestBody QuoteRequest req) {
        QuoteResponse res = rateService.getQuote(req);
        return ResponseEntity.ok(res);
    }
}
