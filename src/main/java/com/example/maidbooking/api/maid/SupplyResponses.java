package com.example.maidbooking.api.maid;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.example.maidbooking.supply.LeaveResult;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftGenerationResult;
import com.example.maidbooking.supply.ShiftStatus;
import com.example.maidbooking.supply.WeeklyShiftTemplate;

/** Response bodies for the shift, template and leave endpoints. */
public final class SupplyResponses {

    private SupplyResponses() {
    }

    public record ShiftResponse(String id, String zone, LocalDateTime start, LocalDateTime end, ShiftStatus status) {
        static ShiftResponse from(Shift shift) {
            return new ShiftResponse(shift.id(), shift.zoneId(), shift.window().start(), shift.window().end(),
                    shift.status());
        }
    }

    public record TemplateResponse(String maidId, List<SupplyRequests.TemplateWindow> windows) {
        static TemplateResponse from(WeeklyShiftTemplate template) {
            return new TemplateResponse(template.maidId(), template.windows().stream()
                    .map(w -> new SupplyRequests.TemplateWindow(w.day(), w.zoneId(), w.start(), w.end())).toList());
        }
    }

    public record GenerationResponse(int created, int skippedOverlapping, int skippedForLeave,
                                     List<ShiftResponse> shifts) {
        static GenerationResponse from(ShiftGenerationResult result) {
            return new GenerationResponse(result.created().size(), result.skippedOverlapping(),
                    result.skippedForLeave(), result.created().stream().map(ShiftResponse::from).toList());
        }
    }

    public record LeaveResponse(String id, LocalDate from, LocalDate to, String reason, List<String> cancelledShiftIds) {
        static LeaveResponse from(LeaveResult result) {
            return new LeaveResponse(result.leave().id(), result.leave().from(), result.leave().to(),
                    result.leave().reason(), result.cancelledShifts().stream().map(Shift::id).toList());
        }
    }
}
