package com.alex.trading_engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
class ActuatorEndpointsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthReturnsUp() {
        ResponseEntity<String> res = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessEndpointAvailable() {
        ResponseEntity<String> res = restTemplate.getForEntity("/actuator/health/liveness", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessEndpointAvailable() {
        ResponseEntity<String> res = restTemplate.getForEntity("/actuator/health/readiness", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void prometheusExposesMatchingMetricsAfterOrder() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"id":"actuator-m1","symbol":"BTC/USD","orderSide":"BUY","price":100,"quantity":1}
                """;
        ResponseEntity<String> orderRes = restTemplate.postForEntity("/order", new HttpEntity<>(body, headers), String.class);
        assertThat(orderRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> prom = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(prom.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prom.getBody()).contains("matching_engine_process_order");
    }
}
