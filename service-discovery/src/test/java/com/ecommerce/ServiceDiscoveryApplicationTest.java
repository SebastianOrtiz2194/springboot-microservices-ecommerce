package com.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Smoke tests for the Eureka server. The context must boot standalone (no peers) and expose the
 * actuator endpoints the platform depends on: health (compose readiness gate) and prometheus
 * (Prometheus scrape job).
 *
 * <p>Spring Boot disables metrics export in tests by default ({@code
 * management.defaults.metrics.export.enabled=false}), so the prometheus export is re-enabled here
 * to exercise the endpoint exactly as production serves it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.prometheus.metrics.export.enabled=true")
class ServiceDiscoveryApplicationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        assertThat(restTemplate).isNotNull();
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void prometheusEndpointExposesScrapeFormat() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("# TYPE");
    }
}
