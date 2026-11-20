package com.example.maidbooking.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The walkthrough in {@code requests.http}, run against the seeded app: the same requests in the same order.
 * Keep the two in step when either changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@SuppressWarnings({"rawtypes", "unchecked"})
class DemoScriptTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private Clock clock;

    /** Two days ahead, skipping Sunday when the seeded maids are off. */
    private LocalDate day() {
        LocalDate day = LocalDate.now(clock).plusDays(2);
        return day.getDayOfWeek() == DayOfWeek.SUNDAY ? day.plusDays(1) : day;
    }

    private Map get(String url) {
        ResponseEntity<Map> response = rest.getForEntity(url, Map.class);
        assertThat(response.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map> post(String url, String json, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), Map.class);
    }

    private static Map firstJob(Map booking) {
        return (Map) ((List) booking.get("jobs")).get(0);
    }

    @Test
    void walkthrough() {
        LocalDate day = day();

        // 1. Slot grid and preferred-maid search
        List<Map> slots = (List<Map>) get("/api/v1/slots?locality=koramangala&serviceType=CLEANING&date=" + day
                + "&durationMinutes=60&tier=PREMIUM").get("slots");
        assertThat(slots).anySatisfy(slot -> {
            assertThat(slot.get("start")).isEqualTo("09:00:00");
            assertThat(slot.get("status")).isNotEqualTo("FULL");
            assertThat(slot.get("price")).isEqualTo(323.7);
        });
        List<Map> maids = (List<Map>) get("/api/v1/maids/search?locality=koramangala&serviceType=CLEANING&start="
                + day + "T09:00&durationMinutes=60&gender=FEMALE&minRating=4.5").get("items");
        assertThat(maids).extracting(maid -> maid.get("maidId")).contains("m-lakshmi");

        // 2. Book Lakshmi for 09:00, pay, get a quote, cancel with a full refund
        ResponseEntity<Map> booked = post("/api/v1/bookings", """
                {"type": "SCHEDULED", "customerId": "c-demo", "locality": "koramangala", "address": "Flat 302",
                 "serviceType": "CLEANING", "tier": "PREMIUM", "start": "%sT09:00", "durationMinutes": 60,
                 "preferredMaidId": "m-lakshmi"}""".formatted(day), null);
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map job = firstJob(booked.getBody());
        assertThat(job.get("maidId")).isEqualTo("m-lakshmi");
        assertThat(booked.getBody().get("amountDueNow")).isEqualTo(323.7);
        String jobId = (String) job.get("jobId");

        ResponseEntity<Map> paid = post("/api/v1/jobs/" + jobId + "/payments",
                "{\"method\": \"UPI\", \"details\": {\"vpa\": \"user@okbank\"}}", "demo-pay-1");
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(paid.getBody().get("jobStatus")).isEqualTo("ASSIGNED");
        assertThat(post("/api/v1/jobs/" + jobId + "/payments",
                "{\"method\": \"UPI\", \"details\": {\"vpa\": \"user@okbank\"}}", "demo-pay-1").getHeaders()
                .getFirst("Idempotent-Replayed")).isEqualTo("true");

        assertThat(get("/api/v1/jobs/" + jobId + "/cancellation-quote").get("refundPercent")).isEqualTo(100);
        ResponseEntity<Map> cancelled = post("/api/v1/jobs/" + jobId + "/cancel", null, null);
        assertThat(cancelled.getBody().get("jobStatus")).isEqualTo("CANCELLED");
        assertThat(cancelled.getBody().get("refundAmount")).isEqualTo(323.7);

        // 3. A standard 10:00 booking: a declined payment, a good one, then the maid drops out
        Map second = post("/api/v1/bookings", """
                {"type": "SCHEDULED", "customerId": "c-demo", "locality": "koramangala", "address": "Flat 302",
                 "serviceType": "CLEANING", "start": "%sT10:00", "durationMinutes": 60,
                 "preferences": {"gender": "FEMALE"}}""".formatted(day), null).getBody();
        String secondJob = (String) firstJob(second).get("jobId");
        ResponseEntity<Map> declined = post("/api/v1/jobs/" + secondJob + "/payments",
                "{\"method\": \"UPI\", \"details\": {\"vpa\": \"decline@okbank\"}}", "demo-pay-2");
        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        Map paidSecond = post("/api/v1/jobs/" + secondJob + "/payments", """
                {"method": "CARD", "details": {"number": "4242 4242 4242 4242", "expiry": "2030-12"}}""",
                "demo-pay-3").getBody();
        String firstMaid = (String) ((Map) paidSecond.get("maid")).get("id");

        Map dropped = post("/api/v1/maids/" + firstMaid + "/jobs/" + secondJob + "/drop", null, null).getBody();
        assertThat(dropped.get("status")).isEqualTo("ASSIGNED");
        assertThat(dropped.get("maidId")).isNotEqualTo(firstMaid);

        // 4. A weekly series with a saved UPI method; the background jobs; cancel the whole series
        ResponseEntity<Map> series = post("/api/v1/bookings", """
                {"type": "RECURRING", "customerId": "c-demo", "locality": "hsr layout", "address": "Villa 12",
                 "serviceType": "CLEANING",
                 "recurrence": {"daysOfWeek": ["%s"], "startTime": "08:00", "durationMinutes": 60,
                                "startDate": "%s", "endDate": "%s"},
                 "savedPayment": {"method": "UPI", "details": {"vpa": "user@okbank"}}}"""
                .formatted(day.getDayOfWeek(), day, day.plusMonths(1)), null);
        assertThat(series.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(series.getBody().get("subscriptionId")).isNotNull();
        assertThat((List) series.getBody().get("jobs")).hasSize(2);
        String bookingId = (String) series.getBody().get("bookingId");

        for (String jobName : List.of("subscriptions", "recurring-charge", "allocation", "hold-sweeper")) {
            assertThat(post("/api/v1/admin/run/" + jobName, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        Map stopped = post("/api/v1/bookings/" + bookingId + "/cancel", null, null).getBody();
        assertThat((List) stopped.get("cancelledJobs")).hasSize(2);
        assertThat(get("/api/v1/bookings/" + bookingId).get("status")).isEqualTo("CANCELLED");
        assertThat((List) rest.getForEntity("/api/v1/customers/c-demo/bookings", List.class).getBody()).hasSize(3);
    }
}
