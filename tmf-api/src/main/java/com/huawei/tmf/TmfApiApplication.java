package com.huawei.tmf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TMF Open APIs Application.
 *
 * Implements TM Forum Open APIs:
 * - TMF638 Service Inventory Management
 * - TMF639 Resource Inventory Management
 * - TMF642 Alarm Management
 */
@SpringBootApplication
public class TmfApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmfApiApplication.class, args);
    }
}
