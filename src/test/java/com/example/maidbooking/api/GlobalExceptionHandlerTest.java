package com.example.maidbooking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.common.error.InvalidStateTransitionException;
import com.example.maidbooking.common.error.NotFoundException;
import com.example.maidbooking.common.error.ValidationException;

class GlobalExceptionHandlerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-23T03:30:00Z"), ZoneId.of("Asia/Kolkata"));

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(FIXED))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void notFoundMapsTo404WithEntityDetails() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Maid m-1 not found"))
                .andExpect(jsonPath("$.details.id").value("m-1"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-23T09:00:00"));
    }

    @Test
    void validationMapsTo400() throws Exception {
        mvc.perform(get("/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void conflictUsesItsOwnCodeAndDetails() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.conflicts[0]").value("2026-11-23T09:00"));
    }

    @Test
    void invalidStateTransitionMapsTo409() throws Exception {
        mvc.perform(get("/invalid-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("Cannot pay Job in state CANCELLED"));
    }

    @Test
    void beanValidationFailureListsFields() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.name").exists());
    }

    @Test
    void malformedJsonMapsTo400() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    enum SampleStatus { CANCELLED }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Maid", "m-1");
        }

        @GetMapping("/invalid")
        void invalid() {
            throw new ValidationException("duration must be a multiple of 30 minutes");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException(ErrorCode.SLOT_UNAVAILABLE, "No capacity",
                    Map.of("conflicts", List.of("2026-11-23T09:00")));
        }

        @GetMapping("/invalid-state")
        void invalidState() {
            throw new InvalidStateTransitionException("Job", SampleStatus.CANCELLED, "pay");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Body body) {
        }
    }
}
