package com.example.maidbooking.api.discovery;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.maidbooking.common.ClockConfig;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.discovery.MaidSearchResult;
import com.example.maidbooking.discovery.MaidSearchService;
import com.example.maidbooking.supply.Gender;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

@WebMvcTest(MaidSearchController.class)
@Import(ClockConfig.class)
class MaidSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MaidSearchService searchService;

    @Test
    void extraParametersBecomeFilters() throws Exception {
        given(searchService.search("koramangala", ServiceType.COOKING, LocalDateTime.of(2026, 11, 23, 9, 0), 120,
                Map.of("minRating", "4", "gender", "FEMALE"), 0, 20)).willReturn(new MaidSearchResult(List.of(
                new MaidSearchResult.Item("m-01", "Lakshmi R", Gender.FEMALE, Tier.PREMIUM, 4.6, Set.of(), "kor-5",
                        Money.of("777.40"))), 0, 20, 1));

        mvc.perform(get("/api/v1/maids/search?locality=koramangala&serviceType=COOKING&start=2026-11-23T09:00"
                        + "&durationMinutes=120&minRating=4&gender=FEMALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].maidId").value("m-01"))
                .andExpect(jsonPath("$.items[0].quotedPrice.amount").value(777.40))
                .andExpect(jsonPath("$.total").value(1));
    }
}
