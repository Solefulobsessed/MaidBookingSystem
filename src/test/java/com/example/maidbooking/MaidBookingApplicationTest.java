package com.example.maidbooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.maidbooking.capacity.CapacityProperties;
import com.example.maidbooking.common.Money;
import com.example.maidbooking.common.TimeSlot;
import com.example.maidbooking.pricing.PriceQuery;
import com.example.maidbooking.pricing.PricingStrategy;
import com.example.maidbooking.supply.ServiceType;
import com.example.maidbooking.supply.Tier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaidBookingApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CapacityProperties capacityProperties;

    @Autowired
    private PricingStrategy pricingStrategy;

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void rateCardIsReadFromConfiguration() {
        TimeSlot hour = TimeSlot.of(LocalDateTime.of(2026, 11, 23, 9, 0), Duration.ofHours(1));

        assertThat(pricingStrategy.price(new PriceQuery(ServiceType.CLEANING, Tier.PREMIUM, hour, "kor-5", false)))
                .isEqualTo(Money.of("323.70"));
    }

    @Test
    void capacitySettingsAreReadFromConfiguration() {
        assertThat(capacityProperties.utilisationFactor()).isEqualTo(0.85);
    }
}
