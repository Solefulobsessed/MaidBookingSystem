package com.example.maidbooking.api.maid;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.api.maid.SupplyRequests.AddLeaveRequest;
import com.example.maidbooking.api.maid.SupplyRequests.AddShiftRequest;
import com.example.maidbooking.api.maid.SupplyRequests.SetTemplateRequest;
import com.example.maidbooking.api.maid.SupplyRequests.TemplateWindow;
import com.example.maidbooking.api.maid.SupplyResponses.GenerationResponse;
import com.example.maidbooking.api.maid.SupplyResponses.LeaveResponse;
import com.example.maidbooking.api.maid.SupplyResponses.ShiftResponse;
import com.example.maidbooking.api.maid.SupplyResponses.TemplateResponse;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.supply.ShiftService;

/** A maid's working pattern, shifts and leave. */
@RestController
@RequestMapping("/api/v1/maids/{maidId}")
public class SupplyController {

    private final ShiftService shiftService;

    public SupplyController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PutMapping("/shift-template")
    public TemplateResponse setTemplate(@PathVariable String maidId, @Valid @RequestBody SetTemplateRequest request) {
        return TemplateResponse.from(shiftService.setTemplate(maidId,
                request.windows().stream().map(TemplateWindow::toTemplate).toList()));
    }

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftResponse addShift(@PathVariable String maidId, @Valid @RequestBody AddShiftRequest request) {
        return ShiftResponse.from(shiftService.addShift(maidId, request.zone(),
                new TimeSlot(request.start(), request.end())));
    }

    @PostMapping(path = "/shifts", params = {"generateFrom", "to"})
    @ResponseStatus(HttpStatus.CREATED)
    public GenerationResponse generateShifts(@PathVariable String maidId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate generateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return GenerationResponse.from(shiftService.generateShifts(maidId, generateFrom, to));
    }

    @PostMapping("/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse addLeave(@PathVariable String maidId, @Valid @RequestBody AddLeaveRequest request) {
        return LeaveResponse.from(shiftService.addLeave(maidId, request.from(), request.to(), request.reason()));
    }
}
