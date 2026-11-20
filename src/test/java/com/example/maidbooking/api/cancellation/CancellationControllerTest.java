package com.example.maidbooking.api.cancellation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.cancellation.CancellationResult;
import com.example.maidbooking.cancellation.CancellationService;
import com.example.maidbooking.cancellation.CancelledBy;
import com.example.maidbooking.cancellation.RefundDecision;
import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;

@WebMvcTest(CancellationController.class)
@Import(ClockConfig.class)
class CancellationControllerTest {

    private static final RefundDecision HALF = new RefundDecision(50, Money.of("124.50"), "Cancelled within 4 h");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CancellationService cancellationService;

    @Test
    void quoteShowsTheRefund() throws Exception {
        given(cancellationService.quote("j-1")).willReturn(HALF);

        mvc.perform(get("/api/v1/jobs/j-1/cancellation-quote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundPercent").value(50))
                .andExpect(jsonPath("$.refundAmount").value(124.5))
                .andExpect(jsonPath("$.jobStatus").doesNotExist());
    }

    @Test
    void cancelIsByTheCustomer() throws Exception {
        given(cancellationService.cancelJob("j-1", CancelledBy.CUSTOMER))
                .willReturn(new CancellationResult("j-1", JobStatus.CANCELLED, HALF));

        mvc.perform(post("/api/v1/jobs/j-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.reason").value("Cancelled within 4 h"));
    }

    @Test
    void aJobThatCanNoLongerBeCancelledIs409() throws Exception {
        given(cancellationService.cancelJob("j-1", CancelledBy.CUSTOMER)).willThrow(new ConflictException(
                ErrorCode.INVALID_STATE, "Job j-1 is EN_ROUTE and can no longer be cancelled", Map.of()));

        mvc.perform(post("/api/v1/jobs/j-1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
