// Package adapter provides adapter creation from configuration.
package adapter

import (
	"log"
	"time"

	"edge-bridge/internal/adapter/modbus"
	"edge-bridge/internal/adapter/mqtt"
	"edge-bridge/internal/adapter/netconf"
	"edge-bridge/internal/adapter/oran"
	"edge-bridge/internal/adapter/snmp"
	"edge-bridge/internal/adapter/types"
	"edge-bridge/internal/config"
)

// CreateFromConfig creates adapters from the configuration and registers them with the manager.
func CreateFromConfig(cfg config.AdaptersConfig) (*Manager, error) {
	managerCfg := ManagerConfig{
		CollectInterval:          cfg.CollectInterval,
		MetricsBufferSize:        10000,
		MaxConcurrentCollections: 10,
		RetryOnFailure:           cfg.RetryOnFailure,
		RetryInterval:            cfg.RetryInterval,
	}

	if managerCfg.CollectInterval == 0 {
		managerCfg.CollectInterval = 30 * time.Second
	}
	if managerCfg.RetryInterval == 0 {
		managerCfg.RetryInterval = 30 * time.Second
	}

	manager := NewManager(managerCfg)

	// Create SNMP adapters
	for _, snmpCfg := range cfg.SNMP {
		if !snmpCfg.Enabled {
			log.Printf("[Factory] Skipping disabled SNMP adapter: %s", snmpCfg.Name)
			continue
		}

		adapter, err := createSNMPAdapter(snmpCfg)
		if err != nil {
			log.Printf("[Factory] Failed to create SNMP adapter %s: %v", snmpCfg.Name, err)
			continue
		}

		if err := manager.Register(adapter); err != nil {
			log.Printf("[Factory] Failed to register SNMP adapter %s: %v", snmpCfg.Name, err)
			continue
		}

		log.Printf("[Factory] Created SNMP adapter: %s -> %s:%d", snmpCfg.Name, snmpCfg.Target, snmpCfg.Port)
	}

	// Create MQTT adapters
	for _, mqttCfg := range cfg.MQTT {
		if !mqttCfg.Enabled {
			log.Printf("[Factory] Skipping disabled MQTT adapter: %s", mqttCfg.Name)
			continue
		}

		adapter, err := createMQTTAdapter(mqttCfg)
		if err != nil {
			log.Printf("[Factory] Failed to create MQTT adapter %s: %v", mqttCfg.Name, err)
			continue
		}

		if err := manager.Register(adapter); err != nil {
			log.Printf("[Factory] Failed to register MQTT adapter %s: %v", mqttCfg.Name, err)
			continue
		}

		log.Printf("[Factory] Created MQTT adapter: %s -> %s", mqttCfg.Name, mqttCfg.Broker)
	}

	// Create NETCONF adapters
	for _, netconfCfg := range cfg.NETCONF {
		if !netconfCfg.Enabled {
			log.Printf("[Factory] Skipping disabled NETCONF adapter: %s", netconfCfg.Name)
			continue
		}

		adapter, err := createNETCONFAdapter(netconfCfg)
		if err != nil {
			log.Printf("[Factory] Failed to create NETCONF adapter %s: %v", netconfCfg.Name, err)
			continue
		}

		if err := manager.Register(adapter); err != nil {
			log.Printf("[Factory] Failed to register NETCONF adapter %s: %v", netconfCfg.Name, err)
			continue
		}

		log.Printf("[Factory] Created NETCONF adapter: %s -> %s:%d", netconfCfg.Name, netconfCfg.Host, netconfCfg.Port)
	}

	// Create Modbus adapters
	for _, modbusCfg := range cfg.Modbus {
		if !modbusCfg.Enabled {
			log.Printf("[Factory] Skipping disabled Modbus adapter: %s", modbusCfg.Name)
			continue
		}

		adapter, err := createModbusAdapter(modbusCfg)
		if err != nil {
			log.Printf("[Factory] Failed to create Modbus adapter %s: %v", modbusCfg.Name, err)
			continue
		}

		if err := manager.Register(adapter); err != nil {
			log.Printf("[Factory] Failed to register Modbus adapter %s: %v", modbusCfg.Name, err)
			continue
		}

		if modbusCfg.Mode == "tcp" {
			log.Printf("[Factory] Created Modbus TCP adapter: %s -> %s:%d", modbusCfg.Name, modbusCfg.Host, modbusCfg.Port)
		} else {
			log.Printf("[Factory] Created Modbus RTU adapter: %s -> %s", modbusCfg.Name, modbusCfg.SerialPort)
		}
	}

	// Create O-RAN O1 adapters
	for _, oranCfg := range cfg.ORAN {
		if !oranCfg.Enabled {
			log.Printf("[Factory] Skipping disabled O-RAN adapter: %s", oranCfg.Name)
			continue
		}

		adapter, err := createORANAdapter(oranCfg)
		if err != nil {
			log.Printf("[Factory] Failed to create O-RAN adapter %s: %v", oranCfg.Name, err)
			continue
		}

		if err := manager.Register(adapter); err != nil {
			log.Printf("[Factory] Failed to register O-RAN adapter %s: %v", oranCfg.Name, err)
			continue
		}

		log.Printf("[Factory] Created O-RAN adapter: %s -> %s:%d (%s)", oranCfg.Name, oranCfg.Host, oranCfg.Port, oranCfg.ComponentType)
	}

	return manager, nil
}

// createSNMPAdapter creates an SNMP adapter from configuration.
func createSNMPAdapter(cfg config.SNMPAdapterConfig) (Adapter, error) {
	port := cfg.Port
	if port == 0 {
		port = 161
	}

	timeout := cfg.Timeout
	if timeout == 0 {
		timeout = 5 * time.Second
	}

	retries := cfg.Retries
	if retries == 0 {
		retries = 2
	}

	// Build snmp.Config with embedded types.Config
	snmpCfg := snmp.Config{
		Config: types.Config{
			Name:          cfg.Name,
			Enabled:       cfg.Enabled,
			Host:          cfg.Target,
			Port:          int(port),
			Timeout:       timeout,
			RetryAttempts: retries,
		},
		Version:       cfg.Version,
		Community:     cfg.Community,
		Username:      cfg.V3.Username,
		AuthProtocol:  cfg.V3.AuthProtocol,
		AuthPassword:  cfg.V3.AuthPassword,
		PrivProtocol:  cfg.V3.PrivProtocol,
		PrivPassword:  cfg.V3.PrivPassword,
		SecurityLevel: cfg.V3.SecurityLevel,
		DeviceType:    cfg.OIDProfile,
	}

	// Add custom OIDs
	for _, oid := range cfg.CustomOIDs {
		snmpCfg.CustomOIDs = append(snmpCfg.CustomOIDs, snmp.OIDMapping{
			OID:        oid.OID,
			MetricType: oid.MetricType,
			Scale:      oid.Scale,
			Offset:     oid.Offset,
		})
	}

	return snmp.New(snmpCfg)
}

// createMQTTAdapter creates an MQTT adapter from configuration.
func createMQTTAdapter(cfg config.MQTTAdapterConfig) (Adapter, error) {
	timeout := 30 * time.Second

	mqttCfg := mqtt.Config{
		Config: types.Config{
			Name:    cfg.Name,
			Enabled: cfg.Enabled,
			Timeout: timeout,
		},
		Broker:       cfg.Broker,
		ClientID:     cfg.ClientID,
		Username:     cfg.Username,
		Password:     cfg.Password,
		UseTLS:       cfg.UseTLS,
		QoS:          cfg.QoS,
		CleanSession: cfg.CleanSession,
	}

	// Convert topic mappings
	for _, topic := range cfg.Topics {
		scale := topic.Scale
		if scale == 0 {
			scale = 1.0
		}
		mqttCfg.Topics = append(mqttCfg.Topics, mqtt.TopicMapping{
			Topic:      topic.Topic,
			MetricType: topic.MetricType,
			ValuePath:  topic.ValuePath,
			Scale:      scale,
			Offset:     topic.Offset,
		})
	}

	return mqtt.New(mqttCfg)
}

// createNETCONFAdapter creates a NETCONF adapter from configuration.
func createNETCONFAdapter(cfg config.NETCONFAdapterConfig) (Adapter, error) {
	port := cfg.Port
	if port == 0 {
		port = 830
	}

	timeout := cfg.Timeout
	if timeout == 0 {
		timeout = 10 * time.Second
	}

	datastoreSource := cfg.DatastoreSource
	if datastoreSource == "" {
		datastoreSource = "running"
	}

	netconfCfg := netconf.Config{
		Config: types.Config{
			Name:    cfg.Name,
			Enabled: cfg.Enabled,
			Host:    cfg.Host,
			Port:    int(port),
			Timeout: timeout,
		},
		Username:        cfg.Username,
		Password:        cfg.Password,
		PrivateKey:      cfg.PrivateKey,
		DeviceType:      cfg.DeviceType,
		DatastoreSource: datastoreSource,
	}

	// Convert custom path mappings
	for _, path := range cfg.CustomPaths {
		scale := path.Scale
		if scale == 0 {
			scale = 1.0
		}
		netconfCfg.CustomPaths = append(netconfCfg.CustomPaths, netconf.PathMapping{
			XPath:       path.XPath,
			MetricType:  path.MetricType,
			Scale:       scale,
			Offset:      path.Offset,
			Description: path.Description,
		})
	}

	return netconf.New(netconfCfg)
}

// createModbusAdapter creates a Modbus adapter from configuration.
func createModbusAdapter(cfg config.ModbusAdapterConfig) (Adapter, error) {
	mode := cfg.Mode
	if mode == "" {
		mode = "tcp"
	}

	port := cfg.Port
	if port == 0 {
		port = 502
	}

	timeout := cfg.Timeout
	if timeout == 0 {
		timeout = 5 * time.Second
	}

	baudRate := cfg.BaudRate
	if baudRate == 0 {
		baudRate = 9600
	}

	dataBits := cfg.DataBits
	if dataBits == 0 {
		dataBits = 8
	}

	parity := cfg.Parity
	if parity == "" {
		parity = "N"
	}

	stopBits := cfg.StopBits
	if stopBits == 0 {
		stopBits = 1
	}

	modbusCfg := modbus.Config{
		Config: types.Config{
			Name:    cfg.Name,
			Enabled: cfg.Enabled,
			Host:    cfg.Host,
			Port:    int(port),
			Timeout: timeout,
		},
		Mode:       mode,
		SerialPort: cfg.SerialPort,
		BaudRate:   baudRate,
		DataBits:   dataBits,
		Parity:     parity,
		StopBits:   stopBits,
		SlaveID:    cfg.SlaveID,
		DeviceType: cfg.DeviceType,
	}

	// Convert custom register mappings
	for _, reg := range cfg.CustomRegisters {
		scale := reg.Scale
		if scale == 0 {
			scale = 1.0
		}
		modbusCfg.CustomRegisters = append(modbusCfg.CustomRegisters, modbus.RegisterMapping{
			Address:      reg.Address,
			RegisterType: reg.RegisterType,
			DataType:     reg.DataType,
			MetricType:   reg.MetricType,
			Scale:        scale,
			Offset:       reg.Offset,
			Description:  reg.Description,
		})
	}

	return modbus.New(modbusCfg)
}

// createORANAdapter creates an O-RAN O1 adapter from configuration.
func createORANAdapter(cfg config.ORANAdapterConfig) (Adapter, error) {
	port := cfg.Port
	if port == 0 {
		port = 830
	}

	timeout := cfg.Timeout
	if timeout == 0 {
		timeout = 10 * time.Second
	}

	componentType := cfg.ComponentType
	if componentType == "" {
		componentType = "o-ru"
	}

	vesPort := cfg.VESPort
	if vesPort == 0 {
		vesPort = 30001
	}

	oranCfg := oran.Config{
		Config: types.Config{
			Name:    cfg.Name,
			Enabled: cfg.Enabled,
			Host:    cfg.Host,
			Port:    int(port),
			Timeout: timeout,
		},
		Username:      cfg.Username,
		Password:      cfg.Password,
		PrivateKey:    cfg.PrivateKey,
		ComponentType: componentType,
		VESEnabled:    cfg.VESEnabled,
		VESPort:       vesPort,
	}

	// Convert custom path mappings
	for _, path := range cfg.CustomPaths {
		scale := path.Scale
		if scale == 0 {
			scale = 1.0
		}
		oranCfg.CustomPaths = append(oranCfg.CustomPaths, oran.PathMapping{
			XPath:       path.XPath,
			MetricType:  path.MetricType,
			Scale:       scale,
			Offset:      path.Offset,
			Description: path.Description,
		})
	}

	return oran.New(oranCfg)
}
