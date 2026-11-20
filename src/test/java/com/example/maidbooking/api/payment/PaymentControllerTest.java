package com.example.maidbooking.api.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.payment.IdempotentResult;
import com.example.maidbooking.payment.PaymentDetails.CardDetails;
import com.example.maidbooking.payment.PaymentDetails.UpiDetails;
import com.example.maidbooking.payment.PaymentFailedException;
import com.example.maidbooking.payment.PaymentReceipt;
import com.example.maidbooking.payment.PaymentService;
import com.example.maidbooking.payment.PaymentStatus;

@WebMvcTest(PaymentController.class)
@Import(ClockConfig.class)
class PaymentControllerTest {

    private static final PaymentReceipt ASSIGNED = new PaymentReceipt("p-3", PaymentStatus.SUCCEEDED, "j-1",
            JobStatus.ASSIGNED, "m-7f3a", "Lakshmi R", 4.6, "4821");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void upiPaymentReturns201WithTheAssignedMaid() throws Exception {
        given(paymentService.pay(eq("j-1"), any(), eq("k-1"))).willReturn(new IdempotentResult<>(ASSIGNED, false));

        mvc.perform(post("/api/v1/jobs/j-1/payments").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\": \"UPI\", \"details\": {\"vpa\": \"user@okbank\"}}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.paymentId").value("p-3"))
                .andExpect(jsonPath("$.jobStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.maid.name").value("Lakshmi R"))
                .andExpect(jsonPath("$.otp").value("4821"));
        verify(paymentService).pay("j-1", new UpiDetails("user@okbank"), "k-1");
    }

    @Test
    void cardDetailsAreReadByMethodAndAReplayIsMarked() throws Exception {
        PaymentReceipt confirmed = new PaymentReceipt("p-4", PaymentStatus.SUCCEEDED, "j-1", JobStatus.CONFIRMED,
                null, null, null, "4821");
        given(paymentService.pay(eq("j-1"), any(), eq("k-2"))).willReturn(new IdempotentResult<>(confirmed, true));

        mvc.perform(post("/api/v1/jobs/j-1/payments").header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\": \"CARD\", \"details\": {\"number\": \"4242424242424242\","
                                + " \"expiry\": \"2027-12\"}}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.maid").isEmpty());
        verify(paymentService).pay("j-1", new CardDetails("4242424242424242", YearMonth.of(2027, 12)), "k-2");
    }

    @Test
    void aDeclineIs402() throws Exception {
        given(paymentService.pay(any(), any(), any())).willThrow(new PaymentFailedException("p-5", "Declined by issuer"));

        mvc.perform(post("/api/v1/jobs/j-1/payments").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\": \"UPI\", \"details\": {\"vpa\": \"decline@okbank\"}}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.details.paymentId").value("p-5"));
    }

    @Test
    void anUnknownMethodIs400() throws Exception {
        mvc.perform(post("/api/v1/jobs/j-1/payments").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\": \"CHEQUE\", \"details\": {}}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentService);
    }
}
