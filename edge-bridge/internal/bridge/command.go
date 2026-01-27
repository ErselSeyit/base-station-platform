// Package bridge provides the main bridge orchestration logic.
package bridge

import (
	"fmt"
	"log"
	"strings"

	"github.com/huawei/edge-bridge/internal/cloud"
	"github.com/huawei/edge-bridge/internal/device"
	"github.com/huawei/edge-bridge/internal/protocol"
)

// CommandExecutor handles command execution from the cloud.
type CommandExecutor struct {
	deviceMgr   *device.Manager
	cloudClient *cloud.Client
	stationID   string
}

// NewCommandExecutor creates a new command executor.
func NewCommandExecutor(deviceMgr *device.Manager, cloudClient *cloud.Client, stationID string) *CommandExecutor {
	return &CommandExecutor{
		deviceMgr:   deviceMgr,
		cloudClient: cloudClient,
		stationID:   stationID,
	}
}

// ProcessPendingCommands fetches and executes pending commands.
func (e *CommandExecutor) ProcessPendingCommands() error {
	commands, err := e.cloudClient.GetPendingCommands(e.stationID)
	if err != nil {
		return fmt.Errorf("failed to get pending commands: %w", err)
	}

	for _, cmd := range commands {
		log.Printf("Processing command: %s (type: %s)", cmd.ID, cmd.Type)
		result := e.executeCommand(&cmd)

		// Report result back to cloud
		if _, err := e.cloudClient.ReportCommandResult(e.stationID, cmd.ID, result); err != nil {
			log.Printf("Failed to report command result: %v", err)
		}
	}

	return nil
}

func (e *CommandExecutor) executeCommand(cmd *cloud.PendingCommand) *cloud.CommandResultRequest {
	cmdType := e.mapCommandType(cmd.Type)
	if cmdType == 0 {
		return &cloud.CommandResultRequest{
			Success: false,
			Error:   fmt.Sprintf("unknown command type: %s", cmd.Type),
		}
	}

	params := e.buildCommandParams(cmd)

	result, err := e.deviceMgr.ExecuteCommand(cmdType, params)
	if err != nil {
		return &cloud.CommandResultRequest{
			Success: false,
			Error:   err.Error(),
		}
	}

	return &cloud.CommandResultRequest{
		Success:    result.Success,
		Output:     result.Output,
		ReturnCode: int(result.ReturnCode),
	}
}

func (e *CommandExecutor) mapCommandType(cloudType string) protocol.CommandType {
	switch strings.ToUpper(cloudType) {
	case "RESTART":
		return protocol.CmdRestart
	case "SHUTDOWN":
		return protocol.CmdShutdown
	case "RESET_CONFIG":
		return protocol.CmdResetConfig
	case "UPDATE_FIRMWARE":
		return protocol.CmdUpdateFirmware
	case "RUN_DIAGNOSTIC":
		return protocol.CmdRunDiagnostic
	case "SET_PARAMETER":
		return protocol.CmdSetParameter
	default:
		return 0
	}
}

func (e *CommandExecutor) buildCommandParams(cmd *cloud.PendingCommand) []byte {
	if cmd.Params == nil {
		return nil
	}

	// Simple serialization of params
	// In production, use a proper encoding
	var parts []string
	for k, v := range cmd.Params {
		parts = append(parts, fmt.Sprintf("%s=%v", k, v))
	}
	return []byte(strings.Join(parts, ";"))
}

// ExecuteLocalCommand executes a command directly without cloud involvement.
func (e *CommandExecutor) ExecuteLocalCommand(cmdType protocol.CommandType, params []byte) (*protocol.CommandResultPayload, error) {
	return e.deviceMgr.ExecuteCommand(cmdType, params)
}
