package io.github.erselseyit.basestation.monitoring.config;

import io.github.erselseyit.basestation.monitoring.service.ThresholdConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes default threshold configurations on application startup.
 *
 * Seeds the MongoDB threshold_config collection with default values
 * from shared-thresholds.json if the collection is empty.
 */
@Component
public class ThresholdConfigInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ThresholdConfigInitializer.class);

    private final ThresholdConfigService thresholdConfigService;

    public ThresholdConfigInitializer(ThresholdConfigService thresholdConfigService) {
        this.thresholdConfigService = thresholdConfigService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking threshold configuration initialization...");
        try {
            thresholdConfigService.initializeDefaults();
        } catch (Exception e) {
            log.error("Failed to initialize threshold configs: {}", e.getMessage(), e);
            // Don't fail startup - the service can still work with hardcoded defaults
        }
    }
}
