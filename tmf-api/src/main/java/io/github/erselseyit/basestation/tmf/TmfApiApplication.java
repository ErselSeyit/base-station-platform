package io.github.erselseyit.basestation.tmf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TMF Open APIs Application.
 *
 * Implements TM Forum Open APIs:
 * - TMF638 Service Inventory Management
 * - TMF639 Resource Inventory Management
 * - TMF642 Alarm Management
 *
 * <p>Scans the shared {@code common} package so the platform's
 * {@code InternalAuthFilter} (the HMAC gate that stops X-User-Role header
 * spoofing) is registered here as it is in the other services.
 */
@SpringBootApplication(scanBasePackages = {
        "io.github.erselseyit.basestation.tmf",
        "io.github.erselseyit.basestation.common"
})
public class TmfApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmfApiApplication.class, args);
    }
}
