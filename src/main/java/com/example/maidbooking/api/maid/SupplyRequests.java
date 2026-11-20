package com.example.maidbooking.api.maid;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.example.maidbooking.supply.ShiftTemplate;

/** Request bodies for the shift, template and leave endpoints. */
public final class SupplyRequests {

    private SupplyRequests() {
    }

    public record TemplateWindow(@NotNull DayOfWeek day, @NotBlank String zone,
                                 @NotNull LocalTime start, @NotNull LocalTime end) {
        ShiftTemplate toTemplate() {
            return new ShiftTemplate(day, zone, start, end);
        }
    }

    public record SetTemplateRequest(@NotEmpty List<@Valid TemplateWindow> windows) {
    }

    public record AddShiftRequest(@NotBlank String zone, @NotNull LocalDateTime start, @NotNull LocalDateTime end) {
    }

    public record AddLeaveRequest(@NotNull LocalDate from, @NotNull LocalDate to, String reason) {
    }
}
