# Per-File Coverage Ledger

> Literal proof-of-coverage. **Every** reviewable source/config/script file in
> the repo has been put through the systematic per-file sweep and carries a
> verdict. `clean` = no issue detected on the audited dimensions. A flag names
> the issue class; every flagged file's fix is in REMEDIATION_PLAN.md.


**Total: 621 files — clean: 524 — flagged: 97**


_Audited dimensions per file: size/SRP, exception handling (empty/bare/broad),
debug prints, TODO/FIXME debt, `any`-typing (TS), mutable-default args (Py),
Dockerfile non-root, obvious secrets._


## (root)  (2/3 clean)

- [x] `Makefile`
- [!] `docker-compose.yml`  — **size:777**
- [x] `pom.xml`

## ai-diagnostic  (22/48 clean)

- [!] `ai-diagnostic/Dockerfile`  — **root-user**
- [!] `ai-diagnostic/anomaly-simulator/Dockerfile`  — **root-user**
- [!] `ai-diagnostic/anomaly-simulator/anomaly_simulator.py`  — **size:553;broad-except**
- [!] `ai-diagnostic/service/alarm_correlation.py`  — **size:602**
- [!] `ai-diagnostic/service/anomaly_detection.py`  — **size:719**
- [!] `ai-diagnostic/service/bi_report_generator.py`  — **size:1252;broad-except**
- [!] `ai-diagnostic/service/computer_vision.py`  — **size:702;broad-except**
- [!] `ai-diagnostic/service/config_drift_detection.py`  — **size:479**
- [!] `ai-diagnostic/service/diagnostic_service.py`  — **size:2808;broad-except**
- [!] `ai-diagnostic/service/digital_twin.py`  — **size:695;broad-except**
- [!] `ai-diagnostic/service/drone_integration.py`  — **size:742**
- [!] `ai-diagnostic/service/generative_ai.py`  — **size:645**
- [!] `ai-diagnostic/service/healing_integration.py`  — **broad-except**
- [!] `ai-diagnostic/service/internal_auth.py`  — **broad-except**
- [x] `ai-diagnostic/service/logging_config.py`
- [!] `ai-diagnostic/service/metrics.py`  — **broad-except**
- [!] `ai-diagnostic/service/predictive_maintenance.py`  — **size:913**
- [!] `ai-diagnostic/service/root_cause_analysis.py`  — **size:603**
- [!] `ai-diagnostic/service/self_healing.py`  — **size:890;broad-except**
- [!] `ai-diagnostic/service/son_functions.py`  — **size:753;broad-except**
- [!] `ai-diagnostic/service/son_scheduler.py`  — **broad-except**
- [!] `ai-diagnostic/service/traffic_prediction.py`  — **size:489**
- [x] `ai-diagnostic/service/utils/__init__.py`
- [x] `ai-diagnostic/service/utils/confidence.py`
- [x] `ai-diagnostic/service/utils/enums.py`
- [x] `ai-diagnostic/service/utils/health.py`
- [x] `ai-diagnostic/service/utils/rng.py`
- [x] `ai-diagnostic/service/utils/serialization.py`
- [x] `ai-diagnostic/service/utils/singleton.py`
- [!] `ai-diagnostic/service/utils/threshold_client.py`  — **broad-except**
- [x] `ai-diagnostic/service/utils/thresholds.py`
- [x] `ai-diagnostic/service/utils/validation.py`
- [x] `ai-diagnostic/service/vision_service.py`
- [x] `ai-diagnostic/tests/__init__.py`
- [x] `ai-diagnostic/tests/conftest.py`
- [x] `ai-diagnostic/tests/test_alarm_x733.py`
- [x] `ai-diagnostic/tests/test_anomaly_detection.py`
- [x] `ai-diagnostic/tests/test_confidence.py`
- [x] `ai-diagnostic/tests/test_health.py`
- [x] `ai-diagnostic/tests/test_rng.py`
- [x] `ai-diagnostic/tests/test_self_healing.py`
- [x] `ai-diagnostic/tests/test_serialization.py`
- [x] `ai-diagnostic/tests/test_threshold_client.py`
- [x] `ai-diagnostic/tests/test_validation.py`
- [!] `ai-diagnostic/virtual-basestation/Dockerfile`  — **root-user**
- [!] `ai-diagnostic/virtual-basestation/device_protocol.py`  — **size:602;broad-except**
- [!] `ai-diagnostic/virtual-basestation/mips_device.py`  — **size:1159;broad-except**
- [!] `ai-diagnostic/virtual-basestation/mips_simulator.py`  — **size:687;broad-except**

## api-gateway  (19/21 clean)

- [!] `api-gateway/Dockerfile`  — **root-user**
- [x] `api-gateway/pom.xml`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/ApiGatewayApplication.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/CorsConfig.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/GatewayFilterConfig.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/LoggingConfig.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/RateLimiterConfig.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/config/WebPropertiesConfig.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/exception/GlobalExceptionHandler.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/exception/package-info.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/filter/HttpsRedirectFilter.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/filter/JwtAuthenticationFilter.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/filter/SecurityHeadersFilter.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/service/TokenRevocationService.java`
- [x] `api-gateway/src/main/java/io/github/erselseyit/basestation/gateway/util/JwtValidator.java`
- [x] `api-gateway/src/main/resources/application.yml`
- [x] `api-gateway/src/main/resources/logback-spring.xml`
- [!] `api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/filter/JwtAuthenticationFilterTest.java`  — **size:427**
- [x] `api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/filter/SecurityHeadersFilterTest.java`
- [x] `api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/util/JwtValidatorTest.java`
- [x] `api-gateway/src/test/java/io/github/erselseyit/basestation/gateway/util/JwtValidatorTokenCasesTest.java`

## auth-service  (31/36 clean)

- [!] `auth-service/Dockerfile`  — **root-user**
- [x] `auth-service/pom.xml`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/AuthServiceApplication.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/DataLoader.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/JwtConfig.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/OpenApiConfig.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/config/SecurityConfig.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/controller/AuthController.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/ErrorResponse.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/LoginRequest.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/LoginResponse.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/RefreshTokenRequest.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/dto/TokenResponse.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/exception/GlobalExceptionHandler.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/model/AuditLog.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/model/RefreshToken.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/model/User.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/repository/AuditLogRepository.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/repository/RefreshTokenRepository.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/repository/UserRepository.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/LoginAttemptService.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/RefreshTokenService.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/SecurityAuditService.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/service/UserService.java`
- [x] `auth-service/src/main/java/io/github/erselseyit/basestation/auth/util/JwtUtil.java`
- [x] `auth-service/src/main/resources/application.yml`
- [x] `auth-service/src/main/resources/logback-spring.xml`
- [x] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/config/JwtConfigTest.java`
- [!] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/controller/AuthControllerTest.java`  — **size:406**
- [x] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/controller/TestSecurityConfig.java`
- [!] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/integration/AuthenticationFlowIntegrationTest.java`  — **size:579**
- [x] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/model/UserDomainTest.java`
- [!] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/service/LoginAttemptServiceTest.java`  — **size:428**
- [!] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/service/UserServiceTest.java`  — **size:415**
- [x] `auth-service/src/test/java/io/github/erselseyit/basestation/auth/util/JwtUtilTest.java`
- [x] `auth-service/src/test/resources/application-test.yml`

## base-station-service  (56/59 clean)

- [!] `base-station-service/Dockerfile`  — **root-user**
- [x] `base-station-service/pom.xml`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/BaseStationServiceApplication.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/client/MonitoringServiceClient.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/CircuitBreakerConfiguration.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/JacksonConfig.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/OpenApiConfig.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/config/SecurityConfig.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/BaseStationController.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/BulkProvisioningController.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/DeviceCommandController.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/EdgeBridgeController.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/controller/package-info.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/BaseStationDTO.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/BulkImportRequest.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/BulkImportResponse.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/dto/package-info.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/exception/GlobalExceptionHandler.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/BaseStation.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/ConnectionProfile.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/DeviceCommand.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/EdgeBridgeInstance.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/ManagementProtocol.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/Organization.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/RFMeasurement.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/SiteVerification.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/StationStatus.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/model/StationType.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/BaseStationRepository.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/ConnectionProfileRepository.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/DeviceCommandRepository.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/EdgeBridgeRepository.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/OrganizationRepository.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/repository/package-info.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/BaseStationService.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/BulkProvisioningService.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/DeviceCommandService.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/EdgeBridgeService.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/service/package-info.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/tenant/TenantContext.java`
- [x] `base-station-service/src/main/java/io/github/erselseyit/basestation/station/tenant/TenantFilter.java`
- [x] `base-station-service/src/main/resources/application.yml`
- [x] `base-station-service/src/main/resources/logback-spring.xml`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/client/MonitoringServiceClientFallbackTest.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/config/TestConfig.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/config/TestSecurityConfig.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/contract/BaseStationContractTestBase.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/contract/ContractTestApplication.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/controller/BaseStationControllerTest.java`
- [!] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/BaseStationIntegrationTest.java`  — **size:447**
- [!] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/BatchMetricsIntegrationTest.java`  — **size:410**
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/IntegrationTestApplication.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/integration/JwtFlowIntegrationTest.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/model/BaseStationDomainTest.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/resilience/MonitoringServiceResilienceTest.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/service/BaseStationServiceTest.java`
- [x] `base-station-service/src/test/java/io/github/erselseyit/basestation/station/test/TestApplication.java`
- [x] `base-station-service/src/test/resources/application-integration-test.yml`
- [x] `base-station-service/src/test/resources/application-test.properties`

## ci  (3/3 clean)

- [x] `.github/workflows/ci.yml`
- [x] `.github/workflows/e2e-test.yml`
- [x] `.github/workflows/load-test.yml`

## common  (44/45 clean)

- [x] `common/pom.xml`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/alarm/PerceivedSeverity.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/audit/AuditLogger.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/audit/package-info.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/config/CacheConfig.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/config/CorrelationIdFilter.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/config/RetryConfig.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/DiagnosticConstants.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/HealthConstants.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/HttpHeaders.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/JsonResponseKeys.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/MessagingConstants.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/PublicEndpoints.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/SecurityConstants.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/ServiceNames.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/TimeConstants.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/constants/ValidationMessages.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/dto/AlertEvent.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/dto/DiagnosticRequest.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/dto/DiagnosticResolutionEvent.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/dto/DiagnosticResponse.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/dto/package-info.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/exception/BaseGlobalExceptionHandler.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/exception/ErrorResponse.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/health/DatabaseConnectionPoolHealthIndicator.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/security/AuthConstants.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/security/InternalAuthFilter.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/security/Permission.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/security/PermissionConfig.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/security/ResourcePermissionEvaluator.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/security/RolePermissions.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/security/Roles.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/util/RequestUtils.java`
- [x] `common/src/main/java/io/github/erselseyit/basestation/common/util/StringUtils.java`
- [x] `common/src/main/resources/shared-thresholds.json`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/alarm/PerceivedSeverityTest.java`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/constants/MessagingConstantsTest.java`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/dto/AlertEventTest.java`
- [!] `common/src/test/java/io/github/erselseyit/basestation/common/dto/DiagnosticRequestTest.java`  — **size:408**
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/dto/DiagnosticResolutionEventTest.java`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/exception/BaseGlobalExceptionHandlerTest.java`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/security/InternalAuthFilterTest.java`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/security/PermissionLookupTest.java`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/security/RolePermissionsTest.java`
- [x] `common/src/test/java/io/github/erselseyit/basestation/common/security/RolesTest.java`

## device-protocol-c  (22/25 clean)

- [x] `device-protocol-c/Makefile`
- [x] `device-protocol-c/examples/host_client.c`
- [!] `device-protocol-c/examples/mips_device.c`  — **size:995**
- [x] `device-protocol-c/fuzz/Makefile`
- [x] `device-protocol-c/fuzz/fuzz_crc16.c`
- [x] `device-protocol-c/fuzz/fuzz_frame_build.c`
- [x] `device-protocol-c/fuzz/fuzz_frame_parser.c`
- [x] `device-protocol-c/include/devproto/crc16.h`
- [x] `device-protocol-c/include/devproto/error.h`
- [x] `device-protocol-c/include/devproto/frame.h`
- [x] `device-protocol-c/include/devproto/metrics.h`
- [x] `device-protocol-c/include/devproto/protocol.h`
- [x] `device-protocol-c/include/devproto/tls.h`
- [x] `device-protocol-c/include/devproto/transport.h`
- [x] `device-protocol-c/src/crc16.c`
- [x] `device-protocol-c/src/frame.c`
- [x] `device-protocol-c/src/metrics.c`
- [x] `device-protocol-c/src/protocol.c`
- [x] `device-protocol-c/src/transport.c`
- [x] `device-protocol-c/src/transport_serial.c`
- [x] `device-protocol-c/src/transport_tcp.c`
- [!] `device-protocol-c/src/transport_tls.c`  — **size:498**
- [x] `device-protocol-c/tests/test_crc16.c`
- [!] `device-protocol-c/tests/test_frame.c`  — **size:475**
- [x] `device-protocol-c/tests/test_metrics.c`

## edge-bridge  (35/44 clean)

- [x] `edge-bridge/Dockerfile`
- [x] `edge-bridge/Makefile`
- [x] `edge-bridge/cmd/edge-bridge/main.go`
- [x] `edge-bridge/configs/bridge-docker.yaml`
- [x] `edge-bridge/configs/bridge.yaml`
- [x] `edge-bridge/internal/adapter/adapter.go`
- [x] `edge-bridge/internal/adapter/adapter_test.go`
- [!] `edge-bridge/internal/adapter/factory.go`  — **size:416**
- [!] `edge-bridge/internal/adapter/manager.go`  — **size:424**
- [x] `edge-bridge/internal/adapter/modbus/adapter.go`
- [!] `edge-bridge/internal/adapter/modbus/registers.go`  — **size:428**
- [!] `edge-bridge/internal/adapter/mqtt/adapter.go`  — **size:436**
- [!] `edge-bridge/internal/adapter/netconf/adapter.go`  — **size:675**
- [x] `edge-bridge/internal/adapter/netconf/paths.go`
- [x] `edge-bridge/internal/adapter/netconf/paths_helpers_test.go`
- [!] `edge-bridge/internal/adapter/oran/adapter.go`  — **size:559**
- [x] `edge-bridge/internal/adapter/oran/paths.go`
- [x] `edge-bridge/internal/adapter/snmp/adapter.go`
- [x] `edge-bridge/internal/adapter/snmp/oids.go`
- [x] `edge-bridge/internal/adapter/types/types.go`
- [x] `edge-bridge/internal/adapter/types/types_test.go`
- [!] `edge-bridge/internal/bridge/bridge.go`  — **size:626**
- [x] `edge-bridge/internal/bridge/buffer.go`
- [x] `edge-bridge/internal/bridge/buffer_test.go`
- [x] `edge-bridge/internal/bridge/command.go`
- [x] `edge-bridge/internal/cloud/auth.go`
- [x] `edge-bridge/internal/cloud/auth_test.go`
- [x] `edge-bridge/internal/cloud/client.go`
- [x] `edge-bridge/internal/cloud/models.go`
- [!] `edge-bridge/internal/config/config.go`  — **size:517**
- [x] `edge-bridge/internal/config/config_test.go`
- [x] `edge-bridge/internal/device/handler.go`
- [x] `edge-bridge/internal/device/handler_test.go`
- [x] `edge-bridge/internal/device/manager.go`
- [x] `edge-bridge/internal/protocol/crc16.go`
- [x] `edge-bridge/internal/protocol/frame.go`
- [!] `edge-bridge/internal/protocol/message.go`  — **size:483**
- [x] `edge-bridge/internal/protocol/metrics.go`
- [x] `edge-bridge/internal/protocol/metrics_test.go`
- [x] `edge-bridge/internal/transport/serial.go`
- [x] `edge-bridge/internal/transport/tcp.go`
- [x] `edge-bridge/internal/transport/tls.go`
- [x] `edge-bridge/internal/transport/transport.go`
- [x] `edge-bridge/internal/transport/transport_test.go`

## frontend  (75/98 clean)

- [x] `frontend/Dockerfile`
- [x] `frontend/e2e/critical-flows.spec.ts`
- [x] `frontend/e2e/dashboard.spec.ts`
- [x] `frontend/e2e/metrics.spec.ts`
- [x] `frontend/e2e/navigation.spec.ts`
- [x] `frontend/e2e/stations.spec.ts`
- [!] `frontend/package-lock.json`  — **size:7505**
- [x] `frontend/package.json`
- [x] `frontend/playwright.config.ts`
- [x] `frontend/public/ai-diagnose-log.json`
- [x] `frontend/src/App.tsx`
- [x] `frontend/src/components/AnimatedCounter.tsx`
- [x] `frontend/src/components/ConfirmDialog.tsx`
- [!] `frontend/src/components/DashboardComponents.tsx`  — **size:634**
- [x] `frontend/src/components/DiagnosticComponents.tsx`
- [x] `frontend/src/components/ErrorBoundary.tsx`
- [x] `frontend/src/components/ErrorDisplay.tsx`
- [x] `frontend/src/components/FeedbackDialog.tsx`
- [x] `frontend/src/components/GlassCard.tsx`
- [x] `frontend/src/components/Layout.tsx`
- [x] `frontend/src/components/LearningStatsCard.tsx`
- [x] `frontend/src/components/LiveActivityFeed.tsx`
- [x] `frontend/src/components/LoadMoreSection.tsx`
- [x] `frontend/src/components/LoadingSpinner.tsx`
- [x] `frontend/src/components/MetricsCategoryChart.tsx`
- [x] `frontend/src/components/MetricsChart.tsx`
- [!] `frontend/src/components/NR5GMetricsCard.tsx`  — **size:448**
- [x] `frontend/src/components/NR5GQuickStatus.tsx`
- [x] `frontend/src/components/PendingConfirmationsCard.tsx`
- [x] `frontend/src/components/PulsingStatus.tsx`
- [x] `frontend/src/components/SkeletonLoader.tsx`
- [!] `frontend/src/components/StationFormDialog.tsx`  — **size:494**
- [x] `frontend/src/components/ThresholdRefreshButton.tsx`
- [x] `frontend/src/components/ToastProvider.tsx`
- [x] `frontend/src/components/__tests__/Layout.test.tsx`
- [x] `frontend/src/components/__tests__/NR5GComponents.test.tsx`
- [x] `frontend/src/constants/colors.ts`
- [!] `frontend/src/constants/designSystem.ts`  — **size:673**
- [!] `frontend/src/constants/metricsConfig.ts`  — **size:469**
- [x] `frontend/src/contexts/ThresholdContext.tsx`
- [x] `frontend/src/hooks/useDashboardData.ts`
- [x] `frontend/src/hooks/useRoutePrefetch.ts`
- [x] `frontend/src/hooks/useThresholdEvaluators.ts`
- [x] `frontend/src/main.tsx`
- [!] `frontend/src/pages/AIDiagnostics.tsx`  — **size:855**
- [!] `frontend/src/pages/Alerts.tsx`  — **size:507**
- [!] `frontend/src/pages/AnalyzeAlert.tsx`  — **size:501**
- [x] `frontend/src/pages/Dashboard.tsx`
- [!] `frontend/src/pages/FiveGDashboard.tsx`  — **size:741**
- [x] `frontend/src/pages/Login.tsx`
- [x] `frontend/src/pages/MapView.tsx`
- [!] `frontend/src/pages/Metrics.tsx`  — **size:610**
- [!] `frontend/src/pages/PowerDashboard.tsx`  — **size:685**
- [!] `frontend/src/pages/Reports.tsx`  — **size:497**
- [!] `frontend/src/pages/SONRecommendations.tsx`  — **size:714**
- [!] `frontend/src/pages/StationDetail.tsx`  — **size:685**
- [!] `frontend/src/pages/Stations.tsx`  — **size:539**
- [x] `frontend/src/pages/__tests__/Alerts.test.tsx`
- [x] `frontend/src/pages/__tests__/Dashboard.test.tsx`
- [x] `frontend/src/pages/__tests__/MapView.test.tsx`
- [!] `frontend/src/pages/__tests__/Metrics.test.tsx`  — **size:417**
- [!] `frontend/src/pages/__tests__/StationDetail.test.tsx`  — **size:468**
- [!] `frontend/src/pages/__tests__/Stations.test.tsx`  — **size:498**
- [x] `frontend/src/services/__tests__/api.test.ts`
- [x] `frontend/src/services/__tests__/authService.test.ts`
- [x] `frontend/src/services/__tests__/tokenManager.test.ts`
- [x] `frontend/src/services/api.ts`
- [x] `frontend/src/services/api/client.ts`
- [x] `frontend/src/services/api/diagnostics.ts`
- [x] `frontend/src/services/api/edgeBridge.ts`
- [x] `frontend/src/services/api/index.ts`
- [x] `frontend/src/services/api/metrics.ts`
- [x] `frontend/src/services/api/notifications.ts`
- [x] `frontend/src/services/api/son.ts`
- [x] `frontend/src/services/api/stations.ts`
- [x] `frontend/src/services/api/thresholds.ts`
- [x] `frontend/src/services/authService.ts`
- [x] `frontend/src/services/logger.ts`
- [x] `frontend/src/services/tokenManager.ts`
- [!] `frontend/src/test/mockHelpers.ts`  — **any-type**
- [x] `frontend/src/test/setup.ts`
- [x] `frontend/src/test/test-utils.tsx`
- [x] `frontend/src/test/vitest-setup.d.ts`
- [x] `frontend/src/types/index.ts`
- [x] `frontend/src/utils/__tests__/arrayUtils.test.ts`
- [x] `frontend/src/utils/__tests__/formatUtils.test.ts`
- [!] `frontend/src/utils/__tests__/metricEvaluators.test.ts`  — **size:480**
- [x] `frontend/src/utils/__tests__/statusHelpers.test.ts`
- [x] `frontend/src/utils/arrayUtils.ts`
- [x] `frontend/src/utils/formatUtils.ts`
- [x] `frontend/src/utils/metricEvaluators.ts`
- [x] `frontend/src/utils/statusHelpers.ts`
- [x] `frontend/src/utils/toast.tsx`
- [!] `frontend/src/vite-env.d.ts`  — **any-type**
- [x] `frontend/tsconfig.json`
- [x] `frontend/tsconfig.node.json`
- [x] `frontend/vite.config.ts`
- [!] `frontend/vitest.config.ts`  — **any-type**

## helm  (45/46 clean)

- [x] `helm/basestation-platform/Chart.yaml`
- [x] `helm/basestation-platform/files/grafana-dashboard-provider.yml`
- [x] `helm/basestation-platform/files/grafana-datasources.yml`
- [x] `helm/basestation-platform/files/loki.yaml`
- [x] `helm/basestation-platform/files/postgres-init.sh`
- [x] `helm/basestation-platform/files/prometheus-alerts.yml`
- [x] `helm/basestation-platform/files/prometheus.yml`
- [x] `helm/basestation-platform/files/promtail.yaml`
- [x] `helm/basestation-platform/templates/databases/mongodb.yaml`
- [x] `helm/basestation-platform/templates/databases/postgres-ha.yaml`
- [x] `helm/basestation-platform/templates/databases/postgres.yaml`
- [x] `helm/basestation-platform/templates/databases/rabbitmq.yaml`
- [x] `helm/basestation-platform/templates/databases/redis.yaml`
- [x] `helm/basestation-platform/templates/ingress.yaml`
- [x] `helm/basestation-platform/templates/istio/authorization-policies.yaml`
- [x] `helm/basestation-platform/templates/istio/destination-rules.yaml`
- [x] `helm/basestation-platform/templates/istio/peer-authentication.yaml`
- [x] `helm/basestation-platform/templates/monitoring/grafana.yaml`
- [x] `helm/basestation-platform/templates/monitoring/loki.yaml`
- [x] `helm/basestation-platform/templates/monitoring/prometheus.yaml`
- [x] `helm/basestation-platform/templates/monitoring/promtail.yaml`
- [x] `helm/basestation-platform/templates/monitoring/zipkin.yaml`
- [x] `helm/basestation-platform/templates/namespace.yaml`
- [x] `helm/basestation-platform/templates/network-policies/app-policies.yaml`
- [x] `helm/basestation-platform/templates/network-policies/db-policies.yaml`
- [x] `helm/basestation-platform/templates/network-policies/default-deny.yaml`
- [x] `helm/basestation-platform/templates/network-policies/monitoring-policies.yaml`
- [x] `helm/basestation-platform/templates/production/hpa.yaml`
- [x] `helm/basestation-platform/templates/production/limit-range.yaml`
- [x] `helm/basestation-platform/templates/production/pdb.yaml`
- [x] `helm/basestation-platform/templates/production/priority-classes.yaml`
- [x] `helm/basestation-platform/templates/production/resource-quota.yaml`
- [x] `helm/basestation-platform/templates/pvcs.yaml`
- [x] `helm/basestation-platform/templates/services/ai-diagnostic.yaml`
- [x] `helm/basestation-platform/templates/services/anomaly-simulator.yaml`
- [x] `helm/basestation-platform/templates/services/api-gateway.yaml`
- [x] `helm/basestation-platform/templates/services/auth-service.yaml`
- [x] `helm/basestation-platform/templates/services/base-station-service.yaml`
- [x] `helm/basestation-platform/templates/services/device-simulator.yaml`
- [x] `helm/basestation-platform/templates/services/edge-bridge.yaml`
- [x] `helm/basestation-platform/templates/services/frontend.yaml`
- [x] `helm/basestation-platform/templates/services/monitoring-service.yaml`
- [x] `helm/basestation-platform/templates/services/notification-service.yaml`
- [x] `helm/basestation-platform/values-dev.yaml`
- [x] `helm/basestation-platform/values-prod.yaml`
- [!] `helm/basestation-platform/values.yaml`  — **size:694**

## init-db  (1/1 clean)

- [x] `init-db/k8s-init-all-databases.sh`

## k8s  (3/3 clean)

- [x] `k8s/create-secrets.sh`
- [x] `k8s/generate-secrets.sh`
- [x] `k8s/sealed-secrets.yaml`

## monitoring-config  (3/3 clean)

- [x] `monitoring/grafana/dashboards/dashboard.yml`
- [x] `monitoring/grafana/datasources/datasource.yml`
- [x] `monitoring/prometheus.yml`

## monitoring-service  (79/86 clean)

- [!] `monitoring-service/Dockerfile`  — **root-user**
- [x] `monitoring-service/pom.xml`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/MonitoringServiceApplication.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/client/DiagnosticClient.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/AlertThresholdConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/CorrelationIdOutboundPostProcessor.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/JacksonConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/LearnedPatternMigration.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/MetricBandMigration.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/OpenApiConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/RabbitMQConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/RabbitMQRetryConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/RedisConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/SecurityConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/ThresholdConfigInitializer.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/config/WebSocketConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/AlertAnalysisController.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/AlertRuleController.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/DiagnosticController.java`
- [!] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/MonitoringController.java`  — **size:527**
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/SONController.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/ThresholdConfigController.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/controller/package-info.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/DailyMetricAggregateDTO.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/MetricCatalogEntryDTO.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/MetricDataDTO.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/dto/package-info.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/event/MetricRecordedEvent.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/exception/GlobalExceptionHandler.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/health/DiagnosticServiceHealthIndicator.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/listener/MetricEventListener.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/AISolution.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/AlertRule.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/AlertSeverity.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/Band.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/DiagnosticSession.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/DiagnosticStatus.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/KPIThreshold.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/LearnedPattern.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/MetricData.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/MetricType.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/MetricUnit.java`
- [!] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/SONRecommendation.java`  — **size:401**
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/SolutionFeedback.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/model/ThresholdConfig.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/DiagnosticSessionRepository.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/LearnedPatternRepository.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/MetricDataRepository.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/SONRecommendationRepository.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/ThresholdConfigRepository.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/repository/package-info.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/AlertParserService.java`
- [!] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/AlertingService.java`  — **size:686**
- [!] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/DiagnosticSessionService.java`  — **size:668**
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/LearningPatternService.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/MonitoringService.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/SONService.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/ThresholdConfigService.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/service/package-info.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/MetricUnitValidator.java`
- [!] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/MetricValueValidator.java`  — **size:544**
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/ValidMetricUnit.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/validation/ValidMetricValue.java`
- [x] `monitoring-service/src/main/java/io/github/erselseyit/basestation/monitoring/websocket/MetricsWebSocketHandler.java`
- [x] `monitoring-service/src/main/resources/application.yml`
- [x] `monitoring-service/src/main/resources/logback-spring.xml`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/config/TestSecurityConfig.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/contract/MonitoringContractTestBase.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/controller/AlertRuleControllerTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/controller/MonitoringControllerBatchTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/controller/MonitoringControllerTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/integration/RabbitMQAlertFlowIntegrationTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/AlertRuleTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/MetricDataDomainTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/MetricTypeThreeGppTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/MetricUnitTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/model/SONRecommendationTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/AlertingServiceTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/AlertingServiceUnitTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/MonitoringServiceBatchTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/MonitoringServiceTest.java`
- [!] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/service/SONServiceTest.java`  — **size:410**
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/support/MongoTestContainerConfig.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/validation/MetricValueValidatorTest.java`
- [x] `monitoring-service/src/test/java/io/github/erselseyit/basestation/monitoring/websocket/MetricsWebSocketHandlerTest.java`
- [x] `monitoring-service/src/test/resources/application-test.yml`

## notification-service  (44/45 clean)

- [!] `notification-service/Dockerfile`  — **root-user**
- [x] `notification-service/pom.xml`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/NotificationServiceApplication.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/AsyncConfig.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/CorrelationIdInboundAdvice.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/JacksonConfig.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/JpaConfig.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/OpenApiConfig.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/RabbitMQConfig.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/SecurityConfig.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/config/ThreadPoolConfig.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/controller/IntegrationController.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/controller/NotificationController.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/controller/package-info.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/dto/NotificationRequest.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/dto/NotificationResponse.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/GlobalExceptionHandler.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/NotificationException.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/NotificationNotFoundException.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/exception/ResourceNotFoundException.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/filter/HeaderAuthenticationFilter.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/AlertDispatcher.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/AlertIntegration.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/PagerDutyService.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/integration/SlackService.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/listener/AlertEventListener.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/listener/DiagnosticResolutionListener.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/model/Notification.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/model/NotificationStatus.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/model/NotificationType.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/repository/NotificationRepository.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/repository/package-info.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/service/AsyncNotificationExecutor.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/service/NotificationService.java`
- [x] `notification-service/src/main/java/io/github/erselseyit/basestation/notification/service/package-info.java`
- [x] `notification-service/src/main/resources/application.yml`
- [x] `notification-service/src/main/resources/logback-spring.xml`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/config/CorrelationIdInboundAdviceTest.java`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/config/TestSecurityConfig.java`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/controller/NotificationControllerTest.java`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/dto/NotificationResponseTest.java`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/listener/AlertEventListenerTest.java`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/listener/DiagnosticResolutionListenerTest.java`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/service/NotificationServiceExtendedTest.java`
- [x] `notification-service/src/test/java/io/github/erselseyit/basestation/notification/service/NotificationServiceTest.java`

## scripts  (11/16 clean)

- [x] `scripts/cleanup-zombies.sh`
- [x] `scripts/create-sealed-secret.sh`
- [x] `scripts/deploy.sh`
- [x] `scripts/generate-tls-certs.sh`
- [x] `scripts/init-multiple-dbs.sh`
- [x] `scripts/k8s-backup-manual.sh`
- [x] `scripts/k8s-restore.sh`
- [!] `scripts/loadtest.py`  — **broad-except**
- [x] `scripts/rotate-secrets.sh`
- [x] `scripts/safe-restart.sh`
- [!] `scripts/seed_historical_metrics.py`  — **bare-except;broad-except**
- [!] `scripts/seed_realistic_data.py`  — **broad-except**
- [!] `scripts/stress_test_comprehensive.py`  — **bare-except;broad-except**
- [!] `scripts/stress_test_gateway.py`  — **broad-except**
- [x] `scripts/test-api.sh`
- [x] `scripts/validate-clean-state.sh`

## testing-harness  (2/10 clean)

- [!] `testing/Dockerfile.simulator`  — **root-user**
- [!] `testing/ai-auto-diagnose.py`  — **size:2150;broad-except**
- [x] `testing/ai-diagnose-log.json`
- [!] `testing/bi-report-generator.py`  — **size:841;broad-except**
- [x] `testing/check-services.sh`
- [!] `testing/device_protocol.py`  — **size:961;broad-except**
- [!] `testing/end-to-end-test.sh`  — **size:512**
- [!] `testing/live-data-simulator.py`  — **size:617;broad-except**
- [!] `testing/mobileinsight-collector.py`  — **size:410;broad-except**
- [!] `testing/real-base-station-collector.py`  — **size:516;broad-except**

## tmf-api  (27/27 clean)

- [x] `tmf-api/pom.xml`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/TmfApiApplication.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/config/OpenApiConfig.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/config/SecurityConfig.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/constants/TMFConstants.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/controller/AlarmManagementController.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/controller/ResourceInventoryController.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/controller/ServiceInventoryController.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/model/Alarm.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/model/Resource.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/model/Service.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/repository/AlarmRepository.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/repository/ResourceRepository.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/repository/ServiceRepository.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/service/AlarmService.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/service/ResourceService.java`
- [x] `tmf-api/src/main/java/io/github/erselseyit/basestation/tmf/service/ServiceInventoryService.java`
- [x] `tmf-api/src/main/resources/application.yml`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/config/SecurityConfigTest.java`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/config/TestSecurityConfig.java`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/controller/AlarmManagementControllerTest.java`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/controller/ResourceInventoryControllerTest.java`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/controller/ServiceInventoryControllerTest.java`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/service/AlarmServiceTest.java`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/service/ResourceServiceTest.java`
- [x] `tmf-api/src/test/java/io/github/erselseyit/basestation/tmf/service/ServiceInventoryServiceTest.java`
- [x] `tmf-api/src/test/resources/application-test.yml`

## virtual-5g-station  (0/2 clean)

- [!] `virtual-5g-station/Dockerfile`  — **root-user**
- [!] `virtual-5g-station/virtual_station.py`  — **size:750;broad-except**
