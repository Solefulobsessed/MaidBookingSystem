package com.example.maidbooking.api.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.maidbooking.booking.Booking;
import com.example.maidbooking.booking.BookingDetails;
import com.example.maidbooking.booking.BookingService;
import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.booking.CreateBookingRequest;
import com.example.maidbooking.booking.Job;
import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.NotFoundException;
import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

@WebMvcTest(BookingController.class)
@Import(ClockConfig.class)
class BookingControllerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 23, 9, 0);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookingService bookingService;

    private static BookingDetails scheduled() {
        Booking booking = new Booking("b-91", "c-1", "kor-5", "Flat 302", ServiceType.COOKING, BookingType.SCHEDULED,
                Tier.STANDARD, MaidFilter.all(List.of()), null, null, START.minusDays(1));
        Job job = new Job("j-1", "b-91", "kor-5", ServiceType.COOKING, new TimeSlot(START, START.plusHours(2)),
                Money.of(598), "cr-1", "4821", START.minusDays(1).plusMinutes(10));
        booking.addJob(job.id());
        return new BookingDetails(booking, List.of(job));
    }

    @Test
    void createReturns201WithTheUnpaidJob() throws Exception {
        given(bookingService.create(any())).willReturn(scheduled());

        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "SCHEDULED", "customerId": "c-1", "locality": "koramangala", "address": "Flat 302",
                         "serviceType": "COOKING", "start": "2026-11-23T09:00", "durationMinutes": 120,
                         "preferences": {"gender": "FEMALE"}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/bookings/b-91"))
                .andExpect(jsonPath("$.bookingId").value("b-91"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.amountDueNow").value(598.0))
                .andExpect(jsonPath("$.jobs[0].jobId").value("j-1"))
                .andExpect(jsonPath("$.jobs[0].start").value("2026-11-23T09:00:00"))
                .andExpect(jsonPath("$.jobs[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.jobs[0].maidId").doesNotExist());

        ArgumentCaptor<CreateBookingRequest> sent = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(bookingService).create(sent.capture());
        assertThat(sent.getValue().start()).isEqualTo(START);
        assertThat(sent.getValue().tier()).isEqualTo(Tier.STANDARD);
        assertThat(sent.getValue().preferences()).containsEntry("gender", "FEMALE");
    }

    @Test
    void missingTypeIs400AndNothingIsCreated() throws Exception {
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": "c-1", "locality": "koramangala", "address": "Flat 302", "serviceType": "COOKING"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(bookingService);
    }

    @Test
    void getReturnsTheBookingAndUnknownIs404() throws Exception {
        given(bookingService.get("b-91")).willReturn(scheduled());
        given(bookingService.get("b-x")).willThrow(new NotFoundException("Booking", "b-x"));

        mvc.perform(get("/api/v1/bookings/b-91"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].otp").value("4821"));
        mvc.perform(get("/api/v1/bookings/b-x")).andExpect(status().isNotFound());
    }

    @Test
    void listsTheCustomersBookings() throws Exception {
        given(bookingService.forCustomer("c-1")).willReturn(List.of(scheduled()));

        mvc.perform(get("/api/v1/customers/c-1/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value("b-91"))
                .andExpect(jsonPath("$[0].type").value("SCHEDULED"));
    }
}
