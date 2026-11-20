package com.example.maidbooking.api.booking;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.maidbooking.booking.Job;
import com.example.maidbooking.booking.JobLifecycleService;
import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.supply.ServiceType;

@WebMvcTest(JobLifecycleController.class)
@Import(ClockConfig.class)
class JobLifecycleControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobLifecycleService lifecycle;

    private static Job onTheWay() {
        LocalDateTime nine = LocalDateTime.of(2026, 11, 23, 9, 0);
        Job job = new Job("j-1", "b-1", "kor-5", ServiceType.CLEANING, new TimeSlot(nine, nine.plusHours(1)),
                Money.of(249), "cr-1", "4821", null);
        job.confirm("p-1");
        job.assign("m-01", "as-1");
        job.enRoute();
        return job;
    }

    @Test
    void enRouteReturnsTheNewStatus() throws Exception {
        given(lifecycle.enRoute("j-1")).willReturn(onTheWay());

        mvc.perform(post("/api/v1/jobs/j-1/en-route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ROUTE"))
                .andExpect(jsonPath("$.maidId").value("m-01"));
    }

    @Test
    void startTakesTheOtpAndAWrongOneIs400() throws Exception {
        given(lifecycle.start("j-1", "1234")).willThrow(new ValidationException("Wrong OTP for job j-1"));

        mvc.perform(post("/api/v1/jobs/j-1/start").contentType(MediaType.APPLICATION_JSON).content("{\"otp\": \"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wrong OTP for job j-1"));
    }

    @Test
    void startWithoutAnOtpIs400() throws Exception {
        mvc.perform(post("/api/v1/jobs/j-1/start").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lifecycle);
    }
}
