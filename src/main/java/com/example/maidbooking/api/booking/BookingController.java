package com.example.maidbooking.api.booking;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.booking.BookingDetails;
import com.example.maidbooking.booking.BookingService;
import com.example.maidbooking.payment.PaymentDetails;
import com.example.maidbooking.payment.SavedPaymentMethods;

@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;
    private final SavedPaymentMethods savedPaymentMethods;

    public BookingController(BookingService bookingService, SavedPaymentMethods savedPaymentMethods) {
        this.bookingService = bookingService;
        this.savedPaymentMethods = savedPaymentMethods;
    }

    /** A series may send {@code savedPayment}: checked first, then kept to charge each later visit. */
    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingBody body) {
        PaymentDetails saved = body.savedPayment() == null ? null : body.savedPayment().details();
        if (saved != null) {
            savedPaymentMethods.check(saved);
        }
        BookingDetails created = bookingService.create(body.toRequest());
        if (saved != null && created.booking().subscriptionId() != null) {
            savedPaymentMethods.save(created.booking().subscriptionId(), saved);
        }
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + created.booking().id()))
                .body(BookingResponse.from(created));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResponse get(@PathVariable String bookingId) {
        return BookingResponse.from(bookingService.get(bookingId));
    }

    @GetMapping("/customers/{customerId}/bookings")
    public List<BookingResponse> forCustomer(@PathVariable String customerId) {
        return bookingService.forCustomer(customerId).stream().map(BookingResponse::from).toList();
    }
}
