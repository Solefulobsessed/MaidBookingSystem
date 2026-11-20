package com.example.maidbooking.api.maid;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.supply.LiveStateService;
import com.example.maidbooking.supply.LiveStatus;
import com.example.maidbooking.supply.MaidLiveState;

@WebMvcTest(LiveStateController.class)
@Import(ClockConfig.class)
class LiveStateControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LiveStateService liveStateService;

    @Test
    void checkIn() throws Exception {
        MaidLiveState idle = new MaidLiveState("m-1");
        idle.moveTo(LiveStatus.IDLE, "kor-5", LocalDateTime.of(2026, 11, 23, 7, 0));
        given(liveStateService.changeStatus("m-1", LiveStatus.IDLE)).willReturn(idle);

        mvc.perform(put("/api/v1/maids/m-1/live-state").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IDLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.zone").value("kor-5"));
    }

    @Test
    void unknownStatusIs400() throws Exception {
        mvc.perform(put("/api/v1/maids/m-1/live-state").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SLEEPING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCurrentState() throws Exception {
        given(liveStateService.get("m-1")).willReturn(new MaidLiveState("m-1"));

        mvc.perform(get("/api/v1/maids/m-1/live-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));
    }
}
