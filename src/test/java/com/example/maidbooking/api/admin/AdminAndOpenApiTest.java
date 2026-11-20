package com.example.maidbooking.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Runs against the full app with the demo data loaded. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAndOpenApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void theOpenApiSpecListsTheBookingAndPaymentEndpoints() {
        ResponseEntity<String> spec = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spec.getBody()).contains("Maid booking API", "/api/v1/bookings", "/api/v1/jobs/{jobId}/payments",
                "/api/v1/slots", "/api/v1/admin/run/{job}");
    }

    @Test
    void swaggerUiLoads() {
        ResponseEntity<String> page = rest.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Swagger UI");
        assertThat(rest.getForEntity("/swagger-ui.html", String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"allocation", "subscriptions", "recurring-charge", "hold-sweeper"})
    @SuppressWarnings("rawtypes")
    void eachBackgroundJobCanBeRunOnDemand(String job) {
        ResponseEntity<Map> run = rest.postForEntity("/api/v1/admin/run/" + job, null, Map.class);

        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(run.getBody()).containsEntry("job", job).containsKey("result");
    }

    @Test
    void anUnknownJobIs404() {
        ResponseEntity<String> run = rest.postForEntity("/api/v1/admin/run/coffee", null, String.class);

        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(run.getBody()).contains("NOT_FOUND", "allocation");
    }
}
