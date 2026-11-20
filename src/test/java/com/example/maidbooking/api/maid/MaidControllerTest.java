package com.example.maidbooking.api.maid;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.common.error.NotFoundException;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidService;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

@WebMvcTest(MaidController.class)
@Import(ClockConfig.class)
class MaidControllerTest {

    private static final String VALID_BODY = """
            {"name": "Lakshmi R", "gender": "FEMALE", "phone": "9876543210", "homeZone": "kor-5",
             "skills": ["CLEANING", "COOKING"], "tags": ["deep-cleaning"], "tier": "PREMIUM"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MaidService maidService;

    private static Maid activeMaid() {
        Maid maid = new Maid("m-7f3a1b2c", "Lakshmi R", Gender.FEMALE, "9876543210",
                Set.of(ServiceType.CLEANING), Set.of("deep-cleaning"), Tier.PREMIUM, "kor-5", 0.0);
        maid.activate();
        return maid;
    }

    @Test
    void onboardReturns201WithLocationAndBody() throws Exception {
        given(maidService.onboard(any())).willReturn(activeMaid());

        mvc.perform(post("/api/v1/maids").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/maids/m-7f3a1b2c"))
                .andExpect(jsonPath("$.id").value("m-7f3a1b2c"))
                .andExpect(jsonPath("$.homeZone").value("kor-5"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rating").value(0.0));
    }

    @Test
    void missingFieldsAreReportedPerField() throws Exception {
        mvc.perform(post("/api/v1/maids").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"skills\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields.name").exists())
                .andExpect(jsonPath("$.details.fields.skills").exists())
                .andExpect(jsonPath("$.details.fields.tier").exists());
    }

    @Test
    void unknownSkillIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/maids").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("COOKING", "GARDENING")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTheMaid() throws Exception {
        given(maidService.get("m-7f3a1b2c")).willReturn(activeMaid());

        mvc.perform(get("/api/v1/maids/m-7f3a1b2c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lakshmi R"))
                .andExpect(jsonPath("$.skills[0]").value("CLEANING"));
    }

    @Test
    void getUnknownMaidIs404() throws Exception {
        given(maidService.get("m-missing")).willThrow(new NotFoundException("Maid", "m-missing"));

        mvc.perform(get("/api/v1/maids/m-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Maid m-missing not found"));
    }
}
