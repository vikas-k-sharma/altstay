package com.altstay.api.booking;

import com.altstay.api.booking.BookingService.BookingResponse;
import com.altstay.api.booking.BookingService.CreateBookingRequest;
import com.altstay.api.booking.BookingService.GuestDto;
import com.altstay.api.booking.BookingService.TransitionRequest;
import com.altstay.api.common.GlobalExceptionHandler;
import com.altstay.api.config.SecurityConfig;
import com.altstay.api.tenancy.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookingController.class, properties = "spring.datasource.url=jdbc:postgresql://slice-test/none")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TenantContextFilter.class})
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/bookings unauthenticated returns 401 Unauthorized")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/bookings/{ref} as FRONT_DESK returns 200 OK")
    void frontDeskCanGetBooking() throws Exception {
        BookingResponse res = new BookingResponse(
                bookingId, "ALT-ABC123", propertyId, guestId,
                new GuestDto(guestId, "Alice", "alice@example.com", null, null, null, null),
                "BOOKED", "DIRECT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                1, 0, "INR", 100000L, 12000L, 112000L, 0L, "UNPAID", null, null,
                List.of(), List.of(), List.of(), false
        );

        when(bookingService.getBooking("ALT-ABC123")).thenReturn(res);

        mockMvc.perform(get("/api/v1/bookings/ALT-ABC123")
                        .with(user("desk@altstay.com").roles("FRONT_DESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("ALT-ABC123"))
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    @DisplayName("POST /api/v1/bookings as FRONT_DESK returns 201 Created")
    void frontDeskCanCreateBooking() throws Exception {
        BookingResponse res = new BookingResponse(
                bookingId, "ALT-NEW001", propertyId, guestId,
                new GuestDto(guestId, "Bob", "bob@example.com", null, null, null, null),
                "BOOKED", "DIRECT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                1, 0, "INR", 100000L, 12000L, 112000L, 0L, "UNPAID", null, null,
                List.of(), List.of(), List.of(), false
        );

        when(bookingService.createBooking(any(CreateBookingRequest.class), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user("desk@altstay.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertySlug": "sunset-lodge",
                                  "guest": { "fullName": "Bob", "email": "bob@example.com" },
                                  "checkIn": "2026-09-01",
                                  "checkOut": "2026-09-03",
                                  "lines": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("ALT-NEW001"));
    }

    @Test
    @DisplayName("POST /api/v1/bookings/{ref}/transitions with valid transition returns 200 OK")
    void validTransitionReturns200() throws Exception {
        BookingResponse res = new BookingResponse(
                bookingId, "ALT-ABC123", propertyId, guestId,
                new GuestDto(guestId, "Alice", "alice@example.com", null, null, null, null),
                "CHECKED_IN", "DIRECT", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                1, 0, "INR", 100000L, 12000L, 112000L, 0L, "UNPAID", null, null,
                List.of(), List.of(), List.of(), false
        );

        when(bookingService.transitionBooking(eq("ALT-ABC123"), any(TransitionRequest.class), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/bookings/ALT-ABC123/transitions")
                        .with(user("desk@altstay.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "to": "CHECKED_IN",
                                  "reason": "Guest arrived"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    @Test
    @DisplayName("POST /api/v1/bookings/{ref}/transitions with illegal transition returns 409 Conflict with invalid-booking-transition problem detail")
    void illegalTransitionReturns409ProblemDetail() throws Exception {
        when(bookingService.transitionBooking(eq("ALT-ABC123"), any(TransitionRequest.class), any()))
                .thenThrow(new InvalidBookingTransitionException("ALT-ABC123", BookingStatus.CHECKED_OUT, BookingStatus.CHECKED_IN));

        mockMvc.perform(post("/api/v1/bookings/ALT-ABC123/transitions")
                        .with(user("desk@altstay.com").roles("FRONT_DESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "to": "CHECKED_IN",
                                  "reason": "Reopen"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.altstay.com/errors/invalid-booking-transition"))
                .andExpect(jsonPath("$.title").value("Invalid Booking Transition"))
                .andExpect(jsonPath("$.reference").value("ALT-ABC123"))
                .andExpect(jsonPath("$.fromStatus").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.toStatus").value("CHECKED_IN"));
    }
}
