// Package main provides the entry point for the edge bridge.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"edge-bridge/internal/bridge"
	"edge-bridge/internal/config"
)

var (
	version   = "0.1.0"
	buildTime = "unknown"
)

func main() {
	// Parse command line flags
	configPath := flag.String("config", "configs/bridge.yaml", "Path to configuration file")
	showVersion := flag.Bool("version", false, "Show version information")
	showHelp := flag.Bool("help", false, "Show help information")

	// Device flags (override config)
	transportType := flag.String("transport", "", "Transport type (serial, tcp)")
	serialPort := flag.String("serial-port", "", "Serial port path")
	serialBaud := flag.Int("serial-baud", 0, "Serial baud rate")
	tcpHost := flag.String("tcp-host", "", "TCP host")
	tcpPort := flag.Int("tcp-port", 0, "TCP port")

	// Bridge flags
	stationID := flag.String("station-id", "", "Station ID")

	flag.Parse()

	if *showHelp {
		printUsage()
		os.Exit(0)
	}

	if *showVersion {
		fmt.Printf("Edge Bridge v%s (built: %s)\n", version, buildTime)
		os.Exit(0)
	}

	// Load configuration
	cfg, err := loadConfig(*configPath)
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// Apply command line overrides
	applyOverrides(cfg, transportType, serialPort, serialBaud, tcpHost, tcpPort, stationID)

	// Validate configuration
	if err := cfg.Validate(); err != nil {
		log.Fatalf("Invalid configuration: %v", err)
	}

	// Create and start bridge
	b, err := bridge.New(cfg)
	if err != nil {
		log.Fatalf("Failed to create bridge: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := b.Start(ctx); err != nil {
		log.Fatalf("Failed to start bridge: %v", err)
	}

	// Wait for shutdown signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	log.Printf("Edge Bridge running. Press Ctrl+C to stop.")

	sig := <-sigCh
	log.Printf("Received signal %v, shutting down...", sig)

	if err := b.Stop(); err != nil {
		log.Printf("Error during shutdown: %v", err)
	}

	log.Printf("Goodbye!")
}

func loadConfig(path string) (*config.Config, error) {
	// Check if config file exists
	if _, err := os.Stat(path); os.IsNotExist(err) {
		log.Printf("Config file not found, using defaults")
		return config.DefaultConfig(), nil
	}

	return config.Load(path)
}

func applyOverrides(cfg *config.Config, transportType, serialPort *string, serialBaud *int, tcpHost *string, tcpPort *int, stationID *string) {
	if *transportType != "" {
		cfg.Device.Transport = *transportType
	}
	if *serialPort != "" {
		cfg.Device.Serial.Port = *serialPort
	}
	if *serialBaud > 0 {
		cfg.Device.Serial.Baud = *serialBaud
	}
	if *tcpHost != "" {
		cfg.Device.TCP.Host = *tcpHost
	}
	if *tcpPort > 0 {
		cfg.Device.TCP.Port = *tcpPort
	}
	if *stationID != "" {
		cfg.Bridge.StationID = *stationID
	}
}

func printUsage() {
	fmt.Printf("Edge Bridge v%s\n", version)
	fmt.Println()
	fmt.Println("A bridge for connecting MIPS edge devices to the cloud backend.")
	fmt.Println()
	fmt.Println("USAGE:")
	fmt.Println("  edge-bridge [OPTIONS]")
	fmt.Println()
	fmt.Println("OPTIONS:")
	fmt.Println("  --config PATH       Configuration file (default: configs/bridge.yaml)")
	fmt.Println("  --transport TYPE    Transport type: serial, tcp")
	fmt.Println("  --serial-port PATH  Serial port path (e.g., /dev/ttyS0)")
	fmt.Println("  --serial-baud RATE  Serial baud rate (default: 115200)")
	fmt.Println("  --tcp-host HOST     TCP host (default: 127.0.0.1)")
	fmt.Println("  --tcp-port PORT     TCP port (default: 9999)")
	fmt.Println("  --station-id ID     Base station ID (default: BS-001)")
	fmt.Println("  --version           Show version")
	fmt.Println("  --help              Show this help")
	fmt.Println()
	fmt.Println("EXAMPLES:")
	fmt.Println("  # Use TCP transport (for testing with Python simulator)")
	fmt.Println("  edge-bridge --transport tcp --tcp-host 127.0.0.1 --tcp-port 9999")
	fmt.Println()
	fmt.Println("  # Use serial transport (for real device)")
	fmt.Println("  edge-bridge --transport serial --serial-port /dev/ttyS0")
	fmt.Println()
	fmt.Println("  # Use custom config file")
	fmt.Println("  edge-bridge --config /etc/edge-bridge/config.yaml")
}
