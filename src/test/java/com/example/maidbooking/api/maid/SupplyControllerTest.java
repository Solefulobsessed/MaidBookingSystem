package com.example.maidbooking.api.maid;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.common.error.ConflictException;
import com.example.maidbooking.common.error.ErrorCode;
import com.example.maidbooking.supply.Leave;
import com.example.maidbooking.supply.LeaveResult;
import com.example.maidbooking.supply.Shift;
import com.example.maidbooking.supply.ShiftGenerationResult;
import com.example.maidbooking.supply.ShiftService;
import com.example.maidbooking.supply.ShiftTemplate;
import com.example.maidbooking.supply.WeeklyShiftTemplate;

@WebMvcTest(SupplyController.class)
@Import(ClockConfig.class)
class SupplyControllerTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ShiftService shiftService;

    private static Shift mondayMorning() {
        return new Shift("s-1", "m-1", "kor-5", new TimeSlot(MON.atTime(7, 0), MON.atTime(13, 0)));
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void setTemplate() throws Exception {
        given(shiftService.setTemplate(eq("m-1"), anyList())).willReturn(new WeeklyShiftTemplate("m-1",
                List.of(new ShiftTemplate(DayOfWeek.MONDAY, "kor-5", LocalTime.of(7, 0), LocalTime.of(13, 0)))));

        mvc.perform(put("/api/v1/maids/m-1/shift-template").contentType(MediaType.APPLICATION_JSON).content("""
                        {"windows": [{"day": "MONDAY", "zone": "kor-5", "start": "07:00", "end": "13:00"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windows[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.windows[0].start").value("07:00:00"));
    }

    @Test
    void templateNeedsWindows() throws Exception {
        mvc.perform(put("/api/v1/maids/m-1/shift-template").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"windows\": [{\"day\": \"MONDAY\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['windows[0].zone']").exists());
    }

    @Test
    void addShift() throws Exception {
        given(shiftService.addShift(eq("m-1"), eq("kor-5"), any())).willReturn(mondayMorning());

        postJson("/api/v1/maids/m-1/shifts",
                "{\"zone\": \"kor-5\", \"start\": \"2026-11-23T07:00\", \"end\": \"2026-11-23T13:00\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s-1"))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void overlappingShiftIs409() throws Exception {
        given(shiftService.addShift(eq("m-1"), eq("kor-5"), any())).willThrow(new ConflictException(
                ErrorCode.SHIFT_CONFLICT, "Shift overlaps an existing shift", Map.of()));

        postJson("/api/v1/maids/m-1/shifts",
                "{\"zone\": \"kor-5\", \"start\": \"2026-11-23T12:00\", \"end\": \"2026-11-23T16:00\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIFT_CONFLICT"));
    }

    @Test
    void generateFromTemplate() throws Exception {
        given(shiftService.generateShifts("m-1", MON, MON.plusDays(6)))
                .willReturn(new ShiftGenerationResult(List.of(mondayMorning()), 2, 1));

        mvc.perform(post("/api/v1/maids/m-1/shifts?generateFrom=2026-11-23&to=2026-11-29"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skippedOverlapping").value(2))
                .andExpect(jsonPath("$.skippedForLeave").value(1))
                .andExpect(jsonPath("$.shifts[0].id").value("s-1"));
    }

    @Test
    void addLeave() throws Exception {
        Shift cancelled = mondayMorning();
        cancelled.cancel();
        given(shiftService.addLeave("m-1", MON, MON, "Fever"))
                .willReturn(new LeaveResult(new Leave("l-1", "m-1", MON, MON, "Fever"), List.of(cancelled)));

        postJson("/api/v1/maids/m-1/leaves", "{\"from\": \"2026-11-23\", \"to\": \"2026-11-23\", \"reason\": \"Fever\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("l-1"))
                .andExpect(jsonPath("$.cancelledShiftIds[0]").value("s-1"));
    }
}
