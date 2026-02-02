package com.huawei.basestation.controller;

import com.huawei.basestation.model.DeviceCommand;
import com.huawei.basestation.service.DeviceCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller for device commands.
 * Provides endpoints for:
 * - Edge-bridge to poll pending commands and report results
 * - AI diagnostic system to queue commands
 * - Operators to view and manage commands
 */
@RestController
@RequestMapping("/api/v1/stations/{stationId}/commands")
@Tag(name = "Device Commands", description = "Command queue for edge devices")
@SecurityRequirement(name = "bearerAuth")
@SuppressWarnings("null") // Spring framework guarantees non-null for validated inputs and responses
public class DeviceCommandController {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandController.class);

    private final DeviceCommandService commandService;

    public DeviceCommandController(DeviceCommandService commandService) {
        this.commandService = commandService;
    }

    /**
     * Get pending commands for a station.
     * Called by edge-bridge to poll for work.
     */
    @Operation(summary = "Get pending commands", description = "Returns commands waiting to be executed by edge-bridge")
    @ApiResponse(responseCode = "200", description = "List of pending commands")
    @GetMapping("/pending")
    public ResponseEntity<List<PendingCommandDTO>> getPendingCommands(@PathVariable Long stationId) {
        List<DeviceCommand> commands = commandService.getPendingCommands(stationId);

        // Mark commands as picked up and convert to DTO
        List<PendingCommandDTO> result = commands.stream()
                .map(cmd -> {
                    commandService.markPickedUp(cmd.getId());
                    return new PendingCommandDTO(
                            cmd.getId(),
                            cmd.getCommandType(),
                            new HashMap<>(cmd.getParams()),
                            cmd.getCreatedAt() != null ? cmd.getCreatedAt().toString() : null
                    );
                })
                .toList();

        if (!result.isEmpty()) {
            log.info("Station {} picked up {} pending commands", stationId, result.size());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Get all commands for a station.
     */
    @Operation(summary = "Get all commands", description = "Returns all commands for a station")
    @ApiResponse(responseCode = "200", description = "List of commands")
    @GetMapping
    public ResponseEntity<List<DeviceCommand>> getAllCommands(@PathVariable Long stationId) {
        return ResponseEntity.ok(commandService.getCommandsForStation(stationId));
    }

    /**
     * Get a specific command.
     */
    @Operation(summary = "Get command by ID", description = "Returns a specific command")
    @ApiResponse(responseCode = "200", description = "Command found")
    @ApiResponse(responseCode = "404", description = "Command not found")
    @GetMapping("/{commandId}")
    public ResponseEntity<DeviceCommand> getCommand(
            @PathVariable Long stationId,
            @PathVariable String commandId) {
        return commandService.getCommand(commandId)
                .filter(cmd -> cmd.getStationId().equals(stationId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Report command execution result.
     * Called by edge-bridge after executing a command.
     */
    @Operation(summary = "Report command result", description = "Reports the result of command execution")
    @ApiResponse(responseCode = "200", description = "Result recorded")
    @ApiResponse(responseCode = "404", description = "Command not found")
    @PostMapping("/{commandId}/result")
    public ResponseEntity<CommandResultResponse> reportResult(
            @PathVariable Long stationId,
            @PathVariable String commandId,
            @Valid @RequestBody CommandResultRequest request) {

        return commandService.recordResult(
                        commandId,
                        request.success(),
                        request.output(),
                        request.returnCode(),
                        request.error())
                .map(cmd -> {
                    log.info("Command {} result recorded: success={}", commandId, request.success());
                    return ResponseEntity.ok(new CommandResultResponse("recorded", "Command result recorded"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a command from AI diagnostic solution.
     * Called by monitoring-service when AI generates a solution.
     */
    @Operation(summary = "Create AI command", description = "Creates a command from AI diagnostic solution")
    @ApiResponse(responseCode = "201", description = "Command created")
    @PostMapping("/ai")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SYSTEM')")
    public ResponseEntity<DeviceCommand> createAICommand(
            @PathVariable Long stationId,
            @Valid @RequestBody AICommandRequest request) {

        DeviceCommand command = commandService.createFromAISolution(
                stationId,
                request.diagnosticSessionId(),
                request.problemCode(),
                request.commandType(),
                Objects.requireNonNullElseGet(request.params(), HashMap::new),
                request.confidence(),
                request.riskLevel()
        );

        log.info("Created AI command {} for station {} (type={}, problem={})",
                command.getId(), stationId, request.commandType(), request.problemCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(command);
    }

    /**
     * Create a manual command (operator-initiated).
     */
    @Operation(summary = "Create manual command", description = "Creates a command manually")
    @ApiResponse(responseCode = "201", description = "Command created")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<DeviceCommand> createCommand(
            @PathVariable Long stationId,
            @Valid @RequestBody ManualCommandRequest request) {

        DeviceCommand command = new DeviceCommand();
        command.setStationId(stationId);
        command.setCommandType(request.commandType());
        command.setParams(Objects.requireNonNullElseGet(request.params(), HashMap::new));
        command.setSource(DeviceCommand.CommandSource.OPERATOR);
        command.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());

        DeviceCommand saved = commandService.createCommand(command);

        log.info("Created manual command {} for station {} (type={})",
                saved.getId(), stationId, request.commandType());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Cancel a pending command.
     */
    @Operation(summary = "Cancel command", description = "Cancels a pending command")
    @ApiResponse(responseCode = "200", description = "Command cancelled")
    @ApiResponse(responseCode = "404", description = "Command not found or not pending")
    @PostMapping("/{commandId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<DeviceCommand> cancelCommand(
            @PathVariable Long stationId,
            @PathVariable String commandId) {

        return commandService.cancelCommand(commandId)
                .filter(cmd -> cmd.getStationId().equals(stationId))
                .map(cmd -> {
                    log.info("Command {} cancelled", commandId);
                    return ResponseEntity.ok(cmd);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========== DTOs ==========

    /**
     * Pending command DTO for edge-bridge (matches Go struct).
     */
    public record PendingCommandDTO(
            String id,
            String type,
            Map<String, String> params,
            String createdAt
    ) {}

    /**
     * Command result request from edge-bridge.
     */
    public record CommandResultRequest(
            @NotNull Boolean success,
            String output,
            Integer returnCode,
            String error
    ) {}

    /**
     * Command result response.
     */
    public record CommandResultResponse(
            String status,
            String message
    ) {}

    /**
     * AI command creation request.
     */
    public record AICommandRequest(
            @NotBlank String diagnosticSessionId,
            @NotBlank String problemCode,
            @NotBlank String commandType,
            Map<String, String> params,
            Double confidence,
            String riskLevel
    ) {}

    /**
     * Manual command creation request.
     */
    public record ManualCommandRequest(
            @NotBlank String commandType,
            Map<String, String> params
    ) {}
}
