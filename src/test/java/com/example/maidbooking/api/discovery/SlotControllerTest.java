package com.example.maidbooking.api.discovery;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.discovery.SlotGrid;
import com.example.maidbooking.discovery.SlotService;
import com.example.maidbooking.discovery.SlotStatus;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

@WebMvcTest(SlotController.class)
@Import(ClockConfig.class)
class SlotControllerTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SlotService slotService;

    @Test
    void returnsTheGridWithStandardTierByDefault() throws Exception {
        given(slotService.slots("koramangala", ServiceType.CLEANING, MON, 60, Tier.STANDARD)).willReturn(
                new SlotGrid("kor-5", MON, List.of(
                        new SlotGrid.Slot(LocalTime.of(9, 0), SlotStatus.FEW_LEFT, Money.of(249)),
                        new SlotGrid.Slot(LocalTime.of(9, 30), SlotStatus.FULL, null))));

        mvc.perform(get("/api/v1/slots?locality=koramangala&serviceType=CLEANING&date=2026-11-23&durationMinutes=60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value("kor-5"))
                .andExpect(jsonPath("$.slots[0].start").value("09:00:00"))
                .andExpect(jsonPath("$.slots[0].status").value("FEW_LEFT"))
                .andExpect(jsonPath("$.slots[0].price.amount").value(249.00))
                .andExpect(jsonPath("$.slots[1].price").doesNotExist());
    }

    @Test
    void missingParameterIs400InOurFormat() throws Exception {
        mvc.perform(get("/api/v1/slots?locality=koramangala&date=2026-11-23&durationMinutes=60"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.parameter").value("serviceType"));
    }

    @Test
    void unknownServiceIs400InOurFormat() throws Exception {
        mvc.perform(get("/api/v1/slots?locality=koramangala&serviceType=GARDENING&date=2026-11-23&durationMinutes=60"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid value 'GARDENING' for 'serviceType'"));
    }
}
