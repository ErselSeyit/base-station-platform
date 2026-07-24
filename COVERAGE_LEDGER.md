# Per-File Coverage Ledger

> Literal proof-of-coverage for the remediation audit. **Every** reviewable
> source/config/script file in the repo is listed here with a status. This is
> the instrument that guarantees nothing escapes the sweep.
>
> Legend: `[x]` = individually read & marked (findings in REMEDIATION_PLAN.md
> or verified clean); `[ ]` = not yet individually read (covered so far only by
> repo-wide detection scans). The sweep fills every `[ ]`.


**Total reviewable files: 621 — individually read so far: 42 — remaining: 579**


## Makefile  (0/1)

- [ ] Makefile

## ai-diagnostic  (8/48)

- [ ] ai-diagnostic/Dockerfile
- [ ] ai-diagnostic/anomaly-simulator/Dockerfile
- [ ] ai-diagnostic/anomaly-simulator/anomaly_simulator.py
- [ ] ai-diagnostic/service/alarm_correlation.py
- [ ] ai-diagnostic/service/anomaly_detection.py
- [ ] ai-diagnostic/service/bi_report_generator.py
- [ ] ai-diagnostic/service/computer_vision.py
- [ ] ai-diagnostic/service/config_drift_detection.py
- [x] ai-diagnostic/service/diagnostic_service.py
- [ ] ai-diagnostic/service/digital_twin.py
- [ ] ai-diagnostic/service/drone_integration.py
- [ ] ai-diagnostic/service/generative_ai.py
- [ ] ai-diagnostic/service/healing_integration.py
- [ ] ai-diagnostic/service/internal_auth.py
- [ ] ai-diagnostic/service/logging_config.py
- [ ] ai-diagnostic/service/metrics.py
- [ ] ai-diagnostic/service/predictive_maintenance.py
- [ ] ai-diagnostic/service/root_cause_analysis.py
- [ ] ai-diagnostic/service/self_healing.py
- [ ] ai-diagnostic/service/son_functions.py
- [ ] ai-diagnostic/service/son_scheduler.py
- [ ] ai-diagnostic/service/traffic_prediction.py
- [ ] ai-diagnostic/service/utils/__init__.py
- [x] ai-diagnostic/service/utils/confidence.py
- [x] ai-diagnostic/service/utils/enums.py
- [x] ai-diagnostic/service/utils/health.py
- [x] ai-diagnostic/service/utils/rng.py
- [x] ai-diagnostic/service/utils/serialization.py
- [ ] ai-diagnostic/service/utils/singleton.py
- [ ] ai-diagnostic/service/utils/threshold_client.py
- [ ] ai-diagnostic/service/utils/thresholds.py
- [ ] ai-diagnostic/service/utils/validation.py
- [ ] ai-diagnostic/service/vision_service.py
- [ ] ai-diagnostic/tests/__init__.py
- [ ] ai-diagnostic/tests/conftest.py
- [ ] ai-diagnostic/tests/test_alarm_x733.py
- [ ] ai-diagnostic/tests/test_anomaly_detection.py
- [ ] ai-diagnostic/tests/test_confidence.py
- [ ] ai-diagnostic/tests/test_health.py
- [ ] ai-diagnostic/tests/test_rng.py
- [ ] ai-diagnostic/tests/test_self_healing.py
- [ ] ai-diagnostic/tests/test_serialization.py
- [ ] ai-diagnostic/tests/test_threshold_client.py
- [ ] ai-diagnostic/tests/test_validation.py
- [ ] ai-diagnostic/virtual-basestation/Dockerfile
- [x] ai-diagnostic/virtual-basestation/device_protocol.py
- [x] ai-diagnostic/virtual-basestation/mips_device.py
- [ ] ai-diagnostic/virtual-basestation/mips_simulator.py

## api-gateway  (3/21)

- [ ] api-gateway/Dockerfile
- [ ] api-gateway/pom.xml
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/ApiGatewayApplication.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/CorsConfig.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/GatewayFilterConfig.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/LoggingConfig.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/RateLimiterConfig.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/WebPropertiesConfig.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/exception/GlobalExceptionHandler.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/exception/package-info.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/filter/HttpsRedirectFilter.java
- [x] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/filter/JwtAuthenticationFilter.java
- [x] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/filter/SecurityHeadersFilter.java
- [ ] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/service/TokenRevocationService.java
- [x] api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/util/JwtValidator.java
- [ ] api-gateway/src/main/resources/application.yml
- [ ] api-gateway/src/main/resources/logback-spring.xml
- [ ] api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/filter/JwtAuthenticationFilterTest.java
- [ ] api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/filter/SecurityHeadersFilterTest.java
- [ ] api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/util/JwtValidatorTest.java
- [ ] api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/util/JwtValidatorTokenCasesTest.java

## auth-service  (3/36)

- [ ] auth-service/Dockerfile
- [ ] auth-service/pom.xml
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/AuthServiceApplication.java
- [x] auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/DataLoader.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/JwtConfig.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/OpenApiConfig.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/SecurityConfig.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/controller/AuthController.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/ErrorResponse.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/LoginRequest.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/LoginResponse.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/RefreshTokenRequest.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/TokenResponse.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/exception/GlobalExceptionHandler.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/model/AuditLog.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/model/RefreshToken.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/model/User.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/repository/AuditLogRepository.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/repository/RefreshTokenRepository.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/repository/UserRepository.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/LoginAttemptService.java
- [x] auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/RefreshTokenService.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/SecurityAuditService.java
- [ ] auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/UserService.java
- [x] auth-service/src/main/java/io/github/erselseyit/basestation/auth/util/JwtUtil.java
- [ ] auth-service/src/main/resources/application.yml
- [ ] auth-service/src/main/resources/logback-spring.xml
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/config/JwtConfigTest.java
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/controller/AuthControllerTest.java
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/controller/TestSecurityConfig.java
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/integration/AuthenticationFlowIntegrationTest.java
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/model/UserDomainTest.java
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/service/LoginAttemptServiceTest.java
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/service/UserServiceTest.java
- [ ] auth-service/src/test/java/io/github/erselseyit/basestation/auth/util/JwtUtilTest.java
- [ ] auth-service/src/test/resources/application-test.yml

## base-station-service  (1/59)

- [ ] base-station-service/Dockerfile
- [ ] base-station-service/pom.xml
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/BaseStationServiceApplication.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/client/MonitoringServiceClient.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/CircuitBreakerConfiguration.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/JacksonConfig.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/OpenApiConfig.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/SecurityConfig.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/BaseStationController.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/BulkProvisioningController.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/DeviceCommandController.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/EdgeBridgeController.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/package-info.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/BaseStationDTO.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/BulkImportRequest.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/BulkImportResponse.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/package-info.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/exception/GlobalExceptionHandler.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/BaseStation.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/ConnectionProfile.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/DeviceCommand.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/EdgeBridgeInstance.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/ManagementProtocol.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/Organization.java
- [x] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/RFMeasurement.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/SiteVerification.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/StationStatus.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/StationType.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/BaseStationRepository.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/ConnectionProfileRepository.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/DeviceCommandRepository.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/EdgeBridgeRepository.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/OrganizationRepository.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/package-info.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/BaseStationService.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/BulkProvisioningService.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/DeviceCommandService.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/EdgeBridgeService.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/package-info.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/tenant/TenantContext.java
- [ ] base-station-service/src/main/java/io/github/erselseyit/basestation/station/tenant/TenantFilter.java
- [ ] base-station-service/src/main/resources/application.yml
- [ ] base-station-service/src/main/resources/logback-spring.xml
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/client/MonitoringServiceClientFallbackTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/config/TestConfig.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/config/TestSecurityConfig.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/contract/BaseStationContractTestBase.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/contract/ContractTestApplication.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/controller/BaseStationControllerTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/BaseStationIntegrationTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/BatchMetricsIntegrationTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/IntegrationTestApplication.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/JwtFlowIntegrationTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/model/BaseStationDomainTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/resilience/MonitoringServiceResilienceTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/service/BaseStationServiceTest.java
- [ ] base-station-service/src/test/java/io/github/erselseyit/basestation/station/test/TestApplication.java
- [ ] base-station-service/src/test/resources/application-integration-test.yml
- [ ] base-station-service/src/test/resources/application-test.properties

## ci  (1/3)

- [x] .github/workflows/ci.yml
- [ ] .github/workflows/e2e-test.yml
- [ ] .github/workflows/load-test.yml

## common  (0/45)

- [ ] common/pom.xml
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/alarm/PerceivedSeverity.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/audit/AuditLogger.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/audit/package-info.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/config/CacheConfig.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/config/CorrelationIdFilter.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/config/RetryConfig.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/DiagnosticConstants.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/HealthConstants.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/HttpHeaders.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/JsonResponseKeys.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/MessagingConstants.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/PublicEndpoints.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/SecurityConstants.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/ServiceNames.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/TimeConstants.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/constants/ValidationMessages.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/dto/AlertEvent.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/dto/DiagnosticRequest.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/dto/DiagnosticResolutionEvent.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/dto/DiagnosticResponse.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/dto/package-info.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/exception/BaseGlobalExceptionHandler.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/exception/ErrorResponse.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/health/DatabaseConnectionPoolHealthIndicator.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/security/AuthConstants.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/security/InternalAuthFilter.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/security/Permission.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/security/PermissionConfig.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/security/ResourcePermissionEvaluator.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/security/RolePermissions.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/security/Roles.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/util/RequestUtils.java
- [ ] common/src/main/java/io/github/erselseyit/basestation/common/util/StringUtils.java
- [ ] common/src/main/resources/shared-thresholds.json
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/alarm/PerceivedSeverityTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/constants/MessagingConstantsTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/dto/AlertEventTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/dto/DiagnosticRequestTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/dto/DiagnosticResolutionEventTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/exception/BaseGlobalExceptionHandlerTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/security/InternalAuthFilterTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/security/PermissionLookupTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/security/RolePermissionsTest.java
- [ ] common/src/test/java/io/github/erselseyit/basestation/common/security/RolesTest.java

## device-protocol-c  (4/25)

- [x] device-protocol-c/Makefile
- [ ] device-protocol-c/examples/host_client.c
- [ ] device-protocol-c/examples/mips_device.c
- [ ] device-protocol-c/fuzz/Makefile
- [ ] device-protocol-c/fuzz/fuzz_crc16.c
- [ ] device-protocol-c/fuzz/fuzz_frame_build.c
- [ ] device-protocol-c/fuzz/fuzz_frame_parser.c
- [ ] device-protocol-c/include/devproto/crc16.h
- [ ] device-protocol-c/include/devproto/error.h
- [ ] device-protocol-c/include/devproto/frame.h
- [ ] device-protocol-c/include/devproto/metrics.h
- [ ] device-protocol-c/include/devproto/protocol.h
- [ ] device-protocol-c/include/devproto/tls.h
- [ ] device-protocol-c/include/devproto/transport.h
- [ ] device-protocol-c/src/crc16.c
- [x] device-protocol-c/src/frame.c
- [ ] device-protocol-c/src/metrics.c
- [ ] device-protocol-c/src/protocol.c
- [ ] device-protocol-c/src/transport.c
- [ ] device-protocol-c/src/transport_serial.c
- [x] device-protocol-c/src/transport_tcp.c
- [x] device-protocol-c/src/transport_tls.c
- [ ] device-protocol-c/tests/test_crc16.c
- [ ] device-protocol-c/tests/test_frame.c
- [ ] device-protocol-c/tests/test_metrics.c

## docker-compose.yml  (1/1)

- [x] docker-compose.yml

## edge-bridge  (8/44)

- [ ] edge-bridge/Dockerfile
- [ ] edge-bridge/Makefile
- [ ] edge-bridge/cmd/edge-bridge/main.go
- [ ] edge-bridge/configs/bridge-docker.yaml
- [ ] edge-bridge/configs/bridge.yaml
- [ ] edge-bridge/internal/adapter/adapter.go
- [ ] edge-bridge/internal/adapter/adapter_test.go
- [ ] edge-bridge/internal/adapter/factory.go
- [ ] edge-bridge/internal/adapter/manager.go
- [ ] edge-bridge/internal/adapter/modbus/adapter.go
- [ ] edge-bridge/internal/adapter/modbus/registers.go
- [ ] edge-bridge/internal/adapter/mqtt/adapter.go
- [ ] edge-bridge/internal/adapter/netconf/adapter.go
- [x] edge-bridge/internal/adapter/netconf/paths.go
- [ ] edge-bridge/internal/adapter/netconf/paths_helpers_test.go
- [ ] edge-bridge/internal/adapter/oran/adapter.go
- [ ] edge-bridge/internal/adapter/oran/paths.go
- [ ] edge-bridge/internal/adapter/snmp/adapter.go
- [ ] edge-bridge/internal/adapter/snmp/oids.go
- [x] edge-bridge/internal/adapter/types/types.go
- [ ] edge-bridge/internal/adapter/types/types_test.go
- [x] edge-bridge/internal/bridge/bridge.go
- [ ] edge-bridge/internal/bridge/buffer.go
- [ ] edge-bridge/internal/bridge/buffer_test.go
- [ ] edge-bridge/internal/bridge/command.go
- [ ] edge-bridge/internal/cloud/auth.go
- [ ] edge-bridge/internal/cloud/auth_test.go
- [x] edge-bridge/internal/cloud/client.go
- [x] edge-bridge/internal/cloud/models.go
- [ ] edge-bridge/internal/config/config.go
- [ ] edge-bridge/internal/config/config_test.go
- [ ] edge-bridge/internal/device/handler.go
- [ ] edge-bridge/internal/device/handler_test.go
- [x] edge-bridge/internal/device/manager.go
- [ ] edge-bridge/internal/protocol/crc16.go
- [ ] edge-bridge/internal/protocol/frame.go
- [ ] edge-bridge/internal/protocol/message.go
- [x] edge-bridge/internal/protocol/metrics.go
- [ ] edge-bridge/internal/protocol/metrics_test.go
- [ ] edge-bridge/internal/transport/serial.go
- [ ] edge-bridge/internal/transport/tcp.go
- [ ] edge-bridge/internal/transport/tls.go
- [x] edge-bridge/internal/transport/transport.go
- [ ] edge-bridge/internal/transport/transport_test.go

## frontend  (5/98)

- [ ] frontend/Dockerfile
- [ ] frontend/e2e/critical-flows.spec.ts
- [ ] frontend/e2e/dashboard.spec.ts
- [ ] frontend/e2e/metrics.spec.ts
- [ ] frontend/e2e/navigation.spec.ts
- [ ] frontend/e2e/stations.spec.ts
- [ ] frontend/package-lock.json
- [ ] frontend/package.json
- [ ] frontend/playwright.config.ts
- [ ] frontend/public/ai-diagnose-log.json
- [ ] frontend/src/App.tsx
- [ ] frontend/src/components/AnimatedCounter.tsx
- [ ] frontend/src/components/ConfirmDialog.tsx
- [ ] frontend/src/components/DashboardComponents.tsx
- [ ] frontend/src/components/DiagnosticComponents.tsx
- [ ] frontend/src/components/ErrorBoundary.tsx
- [ ] frontend/src/components/ErrorDisplay.tsx
- [ ] frontend/src/components/FeedbackDialog.tsx
- [ ] frontend/src/components/GlassCard.tsx
- [ ] frontend/src/components/Layout.tsx
- [ ] frontend/src/components/LearningStatsCard.tsx
- [ ] frontend/src/components/LiveActivityFeed.tsx
- [ ] frontend/src/components/LoadMoreSection.tsx
- [ ] frontend/src/components/LoadingSpinner.tsx
- [ ] frontend/src/components/MetricsCategoryChart.tsx
- [ ] frontend/src/components/MetricsChart.tsx
- [x] frontend/src/components/NR5GMetricsCard.tsx
- [x] frontend/src/components/NR5GQuickStatus.tsx
- [ ] frontend/src/components/PendingConfirmationsCard.tsx
- [ ] frontend/src/components/PulsingStatus.tsx
- [ ] frontend/src/components/SkeletonLoader.tsx
- [ ] frontend/src/components/StationFormDialog.tsx
- [ ] frontend/src/components/ThresholdRefreshButton.tsx
- [ ] frontend/src/components/ToastProvider.tsx
- [ ] frontend/src/components/__tests__/Layout.test.tsx
- [ ] frontend/src/components/__tests__/NR5GComponents.test.tsx
- [ ] frontend/src/constants/colors.ts
- [ ] frontend/src/constants/designSystem.ts
- [x] frontend/src/constants/metricsConfig.ts
- [ ] frontend/src/contexts/ThresholdContext.tsx
- [ ] frontend/src/hooks/useDashboardData.ts
- [ ] frontend/src/hooks/useRoutePrefetch.ts
- [ ] frontend/src/hooks/useThresholdEvaluators.ts
- [ ] frontend/src/main.tsx
- [ ] frontend/src/pages/AIDiagnostics.tsx
- [ ] frontend/src/pages/Alerts.tsx
- [ ] frontend/src/pages/AnalyzeAlert.tsx
- [ ] frontend/src/pages/Dashboard.tsx
- [x] frontend/src/pages/FiveGDashboard.tsx
- [x] frontend/src/pages/Login.tsx
- [ ] frontend/src/pages/MapView.tsx
- [ ] frontend/src/pages/Metrics.tsx
- [ ] frontend/src/pages/PowerDashboard.tsx
- [ ] frontend/src/pages/Reports.tsx
- [ ] frontend/src/pages/SONRecommendations.tsx
- [ ] frontend/src/pages/StationDetail.tsx
- [ ] frontend/src/pages/Stations.tsx
- [ ] frontend/src/pages/__tests__/Alerts.test.tsx
- [ ] frontend/src/pages/__tests__/Dashboard.test.tsx
- [ ] frontend/src/pages/__tests__/MapView.test.tsx
- [ ] frontend/src/pages/__tests__/Metrics.test.tsx
- [ ] frontend/src/pages/__tests__/StationDetail.test.tsx
- [ ] frontend/src/pages/__tests__/Stations.test.tsx
- [ ] frontend/src/services/__tests__/api.test.ts
- [ ] frontend/src/services/__tests__/authService.test.ts
- [ ] frontend/src/services/__tests__/tokenManager.test.ts
- [ ] frontend/src/services/api.ts
- [ ] frontend/src/services/api/client.ts
- [ ] frontend/src/services/api/diagnostics.ts
- [ ] frontend/src/services/api/edgeBridge.ts
- [ ] frontend/src/services/api/index.ts
- [ ] frontend/src/services/api/metrics.ts
- [ ] frontend/src/services/api/notifications.ts
- [ ] frontend/src/services/api/son.ts
- [ ] frontend/src/services/api/stations.ts
- [ ] frontend/src/services/api/thresholds.ts
- [ ] frontend/src/services/authService.ts
- [ ] frontend/src/services/logger.ts
- [ ] frontend/src/services/tokenManager.ts
- [ ] frontend/src/test/mockHelpers.ts
- [ ] frontend/src/test/setup.ts
- [ ] frontend/src/test/test-utils.tsx
- [ ] frontend/src/test/vitest-setup.d.ts
- [ ] frontend/src/types/index.ts
- [ ] frontend/src/utils/__tests__/arrayUtils.test.ts
- [ ] frontend/src/utils/__tests__/formatUtils.test.ts
- [ ] frontend/src/utils/__tests__/metricEvaluators.test.ts
- [ ] frontend/src/utils/__tests__/statusHelpers.test.ts
- [ ] frontend/src/utils/arrayUtils.ts
- [ ] frontend/src/utils/formatUtils.ts
- [ ] frontend/src/utils/metricEvaluators.ts
- [ ] frontend/src/utils/statusHelpers.ts
- [ ] frontend/src/utils/toast.tsx
- [ ] frontend/src/vite-env.d.ts
- [ ] frontend/tsconfig.json
- [ ] frontend/tsconfig.node.json
- [ ] frontend/vite.config.ts
- [ ] frontend/vitest.config.ts

## helm  (0/46)

- [ ] helm/basestation-platform/Chart.yaml
- [ ] helm/basestation-platform/files/grafana-dashboard-provider.yml
- [ ] helm/basestation-platform/files/grafana-datasources.yml
- [ ] helm/basestation-platform/files/loki.yaml
- [ ] helm/basestation-platform/files/postgres-init.sh
- [ ] helm/basestation-platform/files/prometheus-alerts.yml
- [ ] helm/basestation-platform/files/prometheus.yml
- [ ] helm/basestation-platform/files/promtail.yaml
- [ ] helm/basestation-platform/templates/databases/mongodb.yaml
- [ ] helm/basestation-platform/templates/databases/postgres-ha.yaml
- [ ] helm/basestation-platform/templates/databases/postgres.yaml
- [ ] helm/basestation-platform/templates/databases/rabbitmq.yaml
- [ ] helm/basestation-platform/templates/databases/redis.yaml
- [ ] helm/basestation-platform/templates/ingress.yaml
- [ ] helm/basestation-platform/templates/istio/authorization-policies.yaml
- [ ] helm/basestation-platform/templates/istio/destination-rules.yaml
- [ ] helm/basestation-platform/templates/istio/peer-authentication.yaml
- [ ] helm/basestation-platform/templates/monitoring/grafana.yaml
- [ ] helm/basestation-platform/templates/monitoring/loki.yaml
- [ ] helm/basestation-platform/templates/monitoring/prometheus.yaml
- [ ] helm/basestation-platform/templates/monitoring/promtail.yaml
- [ ] helm/basestation-platform/templates/monitoring/zipkin.yaml
- [ ] helm/basestation-platform/templates/namespace.yaml
- [ ] helm/basestation-platform/templates/network-policies/app-policies.yaml
- [ ] helm/basestation-platform/templates/network-policies/db-policies.yaml
- [ ] helm/basestation-platform/templates/network-policies/default-deny.yaml
- [ ] helm/basestation-platform/templates/network-policies/monitoring-policies.yaml
- [ ] helm/basestation-platform/templates/production/hpa.yaml
- [ ] helm/basestation-platform/templates/production/limit-range.yaml
- [ ] helm/basestation-platform/templates/production/pdb.yaml
- [ ] helm/basestation-platform/templates/production/priority-classes.yaml
- [ ] helm/basestation-platform/templates/production/resource-quota.yaml
- [ ] helm/basestation-platform/templates/pvcs.yaml
- [ ] helm/basestation-platform/templates/services/ai-diagnostic.yaml
- [ ] helm/basestation-platform/templates/services/anomaly-simulator.yaml
- [ ] helm/basestation-platform/templates/services/api-gateway.yaml
- [ ] helm/basestation-platform/templates/services/auth-service.yaml
- [ ] helm/basestation-platform/templates/services/base-station-service.yaml
- [ ] helm/basestation-platform/templates/services/device-simulator.yaml
- [ ] helm/basestation-platform/templates/services/edge-bridge.yaml
- [ ] helm/basestation-platform/templates/services/frontend.yaml
- [ ] helm/basestation-platform/templates/services/monitoring-service.yaml
- [ ] helm/basestation-platform/templates/services/notification-service.yaml
- [ ] helm/basestation-platform/values-dev.yaml
- [ ] helm/basestation-platform/values-prod.yaml
- [ ] helm/basestation-platform/values.yaml

## init-db  (0/1)

- [ ] init-db/k8s-init-all-databases.sh

## k8s  (0/3)

- [ ] k8s/create-secrets.sh
- [ ] k8s/generate-secrets.sh
- [ ] k8s/sealed-secrets.yaml

## monitoring-config  (1/3)

- [ ] monitoring/grafana/dashboards/dashboard.yml
- [ ] monitoring/grafana/datasources/datasource.yml
- [x] monitoring/prometheus.yml

## monitoring-service  (5/86)

- [ ] monitoring-service/Dockerfile
- [ ] monitoring-service/pom.xml
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/MonitoringServiceApplication.java
- [x] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/client/DiagnosticClient.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/AlertThresholdConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/CorrelationIdOutboundPostProcessor.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/JacksonConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/LearnedPatternMigration.java
- [x] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/MetricBandMigration.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/OpenApiConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/RabbitMQConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/RabbitMQRetryConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/RedisConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/SecurityConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/ThresholdConfigInitializer.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/WebSocketConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/AlertAnalysisController.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/AlertRuleController.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/DiagnosticController.java
- [x] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/MonitoringController.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/SONController.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/ThresholdConfigController.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/package-info.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/DailyMetricAggregateDTO.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/MetricCatalogEntryDTO.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/MetricDataDTO.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/package-info.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/event/MetricRecordedEvent.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/exception/GlobalExceptionHandler.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/health/DiagnosticServiceHealthIndicator.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/listener/MetricEventListener.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/AISolution.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/AlertRule.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/AlertSeverity.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/Band.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/DiagnosticSession.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/DiagnosticStatus.java
- [x] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/KPIThreshold.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/LearnedPattern.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/MetricData.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/MetricType.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/MetricUnit.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/SONRecommendation.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/SolutionFeedback.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/ThresholdConfig.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/DiagnosticSessionRepository.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/LearnedPatternRepository.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/MetricDataRepository.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/SONRecommendationRepository.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/ThresholdConfigRepository.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/package-info.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/AlertParserService.java
- [x] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/AlertingService.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/DiagnosticSessionService.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/LearningPatternService.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/MonitoringService.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/SONService.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/ThresholdConfigService.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/package-info.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/MetricUnitValidator.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/MetricValueValidator.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/ValidMetricUnit.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/ValidMetricValue.java
- [ ] monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/websocket/MetricsWebSocketHandler.java
- [ ] monitoring-service/src/main/resources/application.yml
- [ ] monitoring-service/src/main/resources/logback-spring.xml
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/config/TestSecurityConfig.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/contract/MonitoringContractTestBase.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/controller/AlertRuleControllerTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/controller/MonitoringControllerBatchTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/controller/MonitoringControllerTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/integration/RabbitMQAlertFlowIntegrationTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/AlertRuleTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/MetricDataDomainTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/MetricTypeThreeGppTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/MetricUnitTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/SONRecommendationTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/AlertingServiceTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/AlertingServiceUnitTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/MonitoringServiceBatchTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/MonitoringServiceTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/SONServiceTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/support/MongoTestContainerConfig.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/validation/MetricValueValidatorTest.java
- [ ] monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/websocket/MetricsWebSocketHandlerTest.java
- [ ] monitoring-service/src/test/resources/application-test.yml

## notification-service  (0/45)

- [ ] notification-service/Dockerfile
- [ ] notification-service/pom.xml
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/NotificationServiceApplication.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/AsyncConfig.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/CorrelationIdInboundAdvice.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/JacksonConfig.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/JpaConfig.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/OpenApiConfig.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/RabbitMQConfig.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/SecurityConfig.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/ThreadPoolConfig.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/controller/IntegrationController.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/controller/NotificationController.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/controller/package-info.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/dto/NotificationRequest.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/dto/NotificationResponse.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/GlobalExceptionHandler.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/NotificationException.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/NotificationNotFoundException.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/ResourceNotFoundException.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/filter/HeaderAuthenticationFilter.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/AlertDispatcher.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/AlertIntegration.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/PagerDutyService.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/SlackService.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/listener/AlertEventListener.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/listener/DiagnosticResolutionListener.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/model/Notification.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/model/NotificationStatus.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/model/NotificationType.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/repository/NotificationRepository.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/repository/package-info.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/service/AsyncNotificationExecutor.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/service/NotificationService.java
- [ ] notification-service/src/main/java/io/github/erselseyit/basestation/notification/service/package-info.java
- [ ] notification-service/src/main/resources/application.yml
- [ ] notification-service/src/main/resources/logback-spring.xml
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/config/CorrelationIdInboundAdviceTest.java
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/config/TestSecurityConfig.java
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/controller/NotificationControllerTest.java
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/dto/NotificationResponseTest.java
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/listener/AlertEventListenerTest.java
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/listener/DiagnosticResolutionListenerTest.java
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/service/NotificationServiceExtendedTest.java
- [ ] notification-service/src/test/java/io/github/erselseyit/basestation/notification/service/NotificationServiceTest.java

## pom.xml  (0/1)

- [ ] pom.xml

## scripts  (0/16)

- [ ] scripts/cleanup-zombies.sh
- [ ] scripts/create-sealed-secret.sh
- [ ] scripts/deploy.sh
- [ ] scripts/generate-tls-certs.sh
- [ ] scripts/init-multiple-dbs.sh
- [ ] scripts/k8s-backup-manual.sh
- [ ] scripts/k8s-restore.sh
- [ ] scripts/loadtest.py
- [ ] scripts/rotate-secrets.sh
- [ ] scripts/safe-restart.sh
- [ ] scripts/seed_historical_metrics.py
- [ ] scripts/seed_realistic_data.py
- [ ] scripts/stress_test_comprehensive.py
- [ ] scripts/stress_test_gateway.py
- [ ] scripts/test-api.sh
- [ ] scripts/validate-clean-state.sh

## testing-harness  (0/10)

- [ ] testing/Dockerfile.simulator
- [ ] testing/ai-auto-diagnose.py
- [ ] testing/ai-diagnose-log.json
- [ ] testing/bi-report-generator.py
- [ ] testing/check-services.sh
- [ ] testing/device_protocol.py
- [ ] testing/end-to-end-test.sh
- [ ] testing/live-data-simulator.py
- [ ] testing/mobileinsight-collector.py
- [ ] testing/real-base-station-collector.py

## tmf-api  (2/27)

- [ ] tmf-api/pom.xml
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/TmfApiApplication.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/config/OpenApiConfig.java
- [x] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/config/SecurityConfig.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/constants/TMFConstants.java
- [x] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/controller/AlarmManagementController.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/controller/ResourceInventoryController.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/controller/ServiceInventoryController.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/model/Alarm.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/model/Resource.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/model/Service.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/repository/AlarmRepository.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/repository/ResourceRepository.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/repository/ServiceRepository.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/service/AlarmService.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/service/ResourceService.java
- [ ] tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/service/ServiceInventoryService.java
- [ ] tmf-api/src/main/resources/application.yml
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/config/SecurityConfigTest.java
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/config/TestSecurityConfig.java
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/controller/AlarmManagementControllerTest.java
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/controller/ResourceInventoryControllerTest.java
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/controller/ServiceInventoryControllerTest.java
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/service/AlarmServiceTest.java
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/service/ResourceServiceTest.java
- [ ] tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/service/ServiceInventoryServiceTest.java
- [ ] tmf-api/src/test/resources/application-test.yml

## virtual-5g-station  (0/2)

- [ ] virtual-5g-station/Dockerfile
- [ ] virtual-5g-station/virtual_station.py
