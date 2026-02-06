#!/usr/bin/env python3
"""
Virtual 5G Base Station Simulator

A realistic 5G NR base station simulator that:
- Generates 5G metrics (throughput, RSRP, SINR, etc.)
- Sends metrics to the monitoring service
- Receives and executes commands from the platform
- Simulates RF conditions and issues
- Supports bidirectional WebSocket communication

Based on Huawei SSV data patterns for realistic behavior.
"""

import asyncio
import json
import logging
import os
import random
import signal
import sys
import time
from dataclasses import dataclass, asdict
from datetime import datetime
from enum import Enum
from typing import Optional, Callable

import aiohttp
from aiohttp import web, WSMsgType
import websockets

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class StationState(Enum):
    STARTING = "STARTING"
    ACTIVE = "ACTIVE"
    MAINTENANCE = "MAINTENANCE"
    DEGRADED = "DEGRADED"
    OFFLINE = "OFFLINE"


class FrequencyBand(Enum):
    NR700 = "NR700_N28"      # 700 MHz, 10 MHz bandwidth
    NR3500 = "NR3500_N78"    # 3.5 GHz, 100 MHz bandwidth


@dataclass
class SectorConfig:
    sector_id: int
    azimuth: int
    pci: int
    earfcn_nr700: int = 156510
    earfcn_nr3500: int = 650976
    tx_power: float = 43.0  # dBm
    electrical_tilt: int = 4


@dataclass
class RFMetrics:
    """5G NR RF metrics based on SSV data patterns."""
    # NR3500 metrics (100 MHz)
    dl_throughput_nr3500: float = 0.0
    ul_throughput_nr3500: float = 0.0
    rsrp_nr3500: float = -75.0
    sinr_nr3500: float = 25.0

    # NR700 metrics (10 MHz)
    dl_throughput_nr700: float = 0.0
    ul_throughput_nr700: float = 0.0
    rsrp_nr700: float = -55.0
    sinr_nr700: float = 20.0

    # Radio parameters
    rank_indicator: int = 4
    avg_mcs: float = 24.0
    rb_per_slot: int = 250
    initial_bler: float = 7.0

    # Quality metrics
    latency: float = 12.0
    tx_imbalance: float = 2.0
    handover_success_rate: float = 100.0

    # CA metrics
    pdcp_throughput: float = 0.0
    rlc_throughput: float = 0.0


@dataclass
class SystemMetrics:
    """System infrastructure metrics."""
    cpu_usage: float = 35.0
    memory_usage: float = 45.0
    temperature: float = 42.0
    power_consumption: float = 4200.0
    fan_speed: float = 2500.0
    uptime: float = 99.9


class Virtual5GStation:
    """Virtual 5G Base Station with realistic behavior."""

    # KPI targets based on Huawei SSV criteria
    KPI_TARGETS = {
        'dl_throughput_nr3500_100mhz': 1000.0,  # >= 1000 Mbps
        'ul_throughput_nr3500_100mhz': 75.0,    # >= 75 Mbps
        'dl_throughput_nr700_10mhz': 50.0,      # >= 50 Mbps
        'ul_throughput_nr700_10mhz': 20.0,      # >= 20 Mbps
        'latency': 15.0,                         # <= 15 ms
        'tx_imbalance': 4.0,                     # <= 4 dB
        'handover_success_rate': 100.0,          # 100%
    }

    def __init__(self, station_id: str, station_name: str,
                 api_gateway_url: str, region: str = "VIRTUAL", city: str = "Simulation"):
        self.station_id = station_id
        self.station_name = station_name
        self.api_gateway_url = api_gateway_url.rstrip('/')
        self.region = region
        self.city = city

        self.state = StationState.STARTING
        self.start_time = datetime.now()

        # Sector configuration (3 sectors typical for macro cell)
        self.sectors = [
            SectorConfig(1, azimuth=0, pci=int(station_id[-3:] + "01") if station_id[-3:].isdigit() else 1001),
            SectorConfig(2, azimuth=120, pci=int(station_id[-3:] + "02") if station_id[-3:].isdigit() else 1002),
            SectorConfig(3, azimuth=240, pci=int(station_id[-3:] + "03") if station_id[-3:].isdigit() else 1003),
        ]

        # Metrics per sector
        self.rf_metrics = {s.sector_id: RFMetrics() for s in self.sectors}
        self.system_metrics = SystemMetrics()

        # Simulation parameters
        self.simulation_speed = 1.0  # Time multiplier
        self.inject_issue = None     # Current injected issue
        self.issue_severity = 0.0    # 0-1 severity scale

        # Command handlers
        self.command_handlers: dict[str, Callable] = {
            'REBOOT': self._handle_reboot,
            'SET_POWER': self._handle_set_power,
            'SET_TILT': self._handle_set_tilt,
            'INJECT_ISSUE': self._handle_inject_issue,
            'CLEAR_ISSUE': self._handle_clear_issue,
            'GET_STATUS': self._handle_get_status,
            'SET_STATE': self._handle_set_state,
        }

        # WebSocket connections
        self.ws_clients: set = set()
        self.running = False

        # HTTP session
        self.session: Optional[aiohttp.ClientSession] = None
        self.auth_token: Optional[str] = None

        logger.info(f"Virtual 5G Station initialized: {station_name} ({station_id})")

    async def start(self):
        """Start the virtual station."""
        self.running = True
        self.state = StationState.ACTIVE
        self.session = aiohttp.ClientSession()

        # Authenticate with the platform
        await self._authenticate()

        # Register station if it doesn't exist
        await self._register_station()

        logger.info(f"Station {self.station_name} started")

        # Start background tasks
        asyncio.create_task(self._metrics_loop())
        asyncio.create_task(self._heartbeat_loop())

    async def stop(self):
        """Stop the virtual station."""
        self.running = False
        self.state = StationState.OFFLINE

        if self.session:
            await self.session.close()

        # Close all WebSocket connections
        for ws in self.ws_clients.copy():
            await ws.close()

        logger.info(f"Station {self.station_name} stopped")

    async def _authenticate(self):
        """Authenticate with the API gateway."""
        try:
            password = os.environ.get("STATION_PASSWORD") or os.environ.get("AUTH_ADMIN_PASSWORD")
            if not password:
                raise ValueError("STATION_PASSWORD or AUTH_ADMIN_PASSWORD environment variable is required")

            auth_url = f"{self.api_gateway_url}/api/v1/auth/login"
            auth_data = {
                "username": os.environ.get("STATION_USERNAME", "admin"),
                "password": password
            }

            async with self.session.post(auth_url, json=auth_data) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    self.auth_token = data.get("token")
                    logger.info("Successfully authenticated with platform")
                else:
                    logger.warning(f"Authentication failed: {resp.status}")
        except Exception as e:
            logger.error(f"Authentication error: {e}")

    async def _register_station(self):
        """Register this virtual station with the platform if it doesn't exist."""
        if not self.auth_token:
            logger.warning("Cannot register station: not authenticated")
            return

        try:
            headers = {"Authorization": f"Bearer {self.auth_token}"}
            stations_url = f"{self.api_gateway_url}/api/v1/stations"

            # Check if station already exists
            async with self.session.get(stations_url, headers=headers) as resp:
                if resp.status == 200:
                    stations = await resp.json()
                    # Check if our station ID exists
                    if any(s.get('id') == self.station_id or s.get('stationName') == self.station_name for s in stations):
                        logger.info(f"Station {self.station_name} already registered")
                        return

            # Register new station
            station_data = {
                "stationName": self.station_name,
                "location": f"{self.city}, {self.region}",
                "latitude": 39.9042,  # Default coordinates
                "longitude": 116.4074,
                "stationType": "MACRO_CELL",
                "status": "ACTIVE",
                "powerConsumption": 4200.0,
                "description": f"Virtual 5G station for testing and simulation"
            }

            async with self.session.post(stations_url, json=station_data, headers=headers) as resp:
                if resp.status in (200, 201):
                    logger.info(f"Successfully registered station {self.station_name}")
                else:
                    logger.warning(f"Failed to register station: {resp.status}")
        except Exception as e:
            logger.error(f"Station registration error: {e}")

    async def _metrics_loop(self):
        """Main loop for generating and sending metrics."""
        while self.running:
            try:
                # Update metrics with realistic variations
                self._update_metrics()

                # Send metrics to platform
                await self._send_metrics()

                # Broadcast to WebSocket clients
                await self._broadcast_metrics()

                # Wait before next update (default 5 seconds)
                await asyncio.sleep(5.0 / self.simulation_speed)

            except Exception as e:
                logger.error(f"Metrics loop error: {e}")
                await asyncio.sleep(1)

    async def _heartbeat_loop(self):
        """Send periodic heartbeats to the platform."""
        while self.running:
            try:
                await self._send_heartbeat()
                await asyncio.sleep(30)
            except Exception as e:
                logger.error(f"Heartbeat error: {e}")
                await asyncio.sleep(5)

    def _update_metrics(self):
        """Update all metrics with realistic variations."""
        for sector_id, metrics in self.rf_metrics.items():
            # Base performance (good conditions)
            base_dl_3500 = 1250.0 + random.gauss(0, 50)
            base_ul_3500 = 95.0 + random.gauss(0, 10)
            base_dl_700 = 85.0 + random.gauss(0, 5)
            base_ul_700 = 27.0 + random.gauss(0, 2)

            # Apply issue effects
            if self.inject_issue:
                base_dl_3500, base_ul_3500 = self._apply_issue_effect(
                    base_dl_3500, base_ul_3500, metrics
                )

            # NR3500 metrics
            metrics.dl_throughput_nr3500 = max(0, base_dl_3500)
            metrics.ul_throughput_nr3500 = max(0, base_ul_3500)
            metrics.rsrp_nr3500 = -75 + random.gauss(0, 3) + (self.issue_severity * -10)
            metrics.sinr_nr3500 = 25 + random.gauss(0, 3) - (self.issue_severity * 10)

            # NR700 metrics
            metrics.dl_throughput_nr700 = max(0, base_dl_700)
            metrics.ul_throughput_nr700 = max(0, base_ul_700)
            metrics.rsrp_nr700 = -55 + random.gauss(0, 2)
            metrics.sinr_nr700 = 20 + random.gauss(0, 2)

            # Radio parameters
            metrics.rank_indicator = 4 if metrics.sinr_nr3500 > 15 else (2 if metrics.sinr_nr3500 > 5 else 1)
            metrics.avg_mcs = min(28, max(0, 24 + random.gauss(0, 2)))
            metrics.rb_per_slot = int(250 + random.gauss(0, 5))
            metrics.initial_bler = max(0, min(100, 7 + random.gauss(0, 1) + (self.issue_severity * 5)))

            # Quality metrics
            metrics.latency = max(1, 12 + random.gauss(0, 1) + (self.issue_severity * 5))

            # TX imbalance - critical metric
            if self.inject_issue == 'TX_IMBALANCE':
                metrics.tx_imbalance = 4 + (self.issue_severity * 15)  # Can go up to 19 dB
            else:
                metrics.tx_imbalance = max(0, 2 + random.gauss(0, 0.5))

            metrics.handover_success_rate = max(0, min(100, 100 - (self.issue_severity * 5)))

            # CA throughput
            metrics.pdcp_throughput = metrics.dl_throughput_nr3500 * 1.25
            metrics.rlc_throughput = metrics.dl_throughput_nr3500 * 1.1

        # System metrics
        self.system_metrics.cpu_usage = max(0, min(100, 35 + random.gauss(0, 5)))
        self.system_metrics.memory_usage = max(0, min(100, 45 + random.gauss(0, 3)))
        self.system_metrics.temperature = max(20, min(80, 42 + random.gauss(0, 2)))
        self.system_metrics.power_consumption = max(0, 4200 + random.gauss(0, 100))
        self.system_metrics.fan_speed = max(1000, 2500 + random.gauss(0, 200))

        # Calculate uptime
        uptime_hours = (datetime.now() - self.start_time).total_seconds() / 3600
        self.system_metrics.uptime = min(99.99, 99.9 + random.gauss(0, 0.01))

    def _apply_issue_effect(self, dl: float, ul: float, metrics: RFMetrics) -> tuple:
        """Apply effects of injected issues."""
        severity = self.issue_severity

        if self.inject_issue == 'THROUGHPUT_DROP':
            dl *= (1 - severity * 0.5)
            ul *= (1 - severity * 0.5)
        elif self.inject_issue == 'INTERFERENCE':
            dl *= (1 - severity * 0.3)
            metrics.sinr_nr3500 -= severity * 15
        elif self.inject_issue == 'HARDWARE_FAULT':
            dl *= (1 - severity * 0.7)
            ul *= (1 - severity * 0.7)
            self.system_metrics.temperature += severity * 20

        return dl, ul

    async def _send_metrics(self):
        """Send metrics to the monitoring service."""
        if not self.auth_token:
            await self._authenticate()
            if not self.auth_token:
                return

        headers = {"Authorization": f"Bearer {self.auth_token}"}
        metrics_url = f"{self.api_gateway_url}/api/v1/metrics/batch"

        # Build metrics list in the format expected by the batch API
        metrics_list = []

        for sector_id, rf in self.rf_metrics.items():
            # Add all RF metrics for this sector
            metrics_list.extend([
                {"type": "DL_THROUGHPUT_NR3500", "value": rf.dl_throughput_nr3500},
                {"type": "UL_THROUGHPUT_NR3500", "value": rf.ul_throughput_nr3500},
                {"type": "RSRP_NR3500", "value": rf.rsrp_nr3500},
                {"type": "SINR_NR3500", "value": rf.sinr_nr3500},
                {"type": "DL_THROUGHPUT_NR700", "value": rf.dl_throughput_nr700},
                {"type": "UL_THROUGHPUT_NR700", "value": rf.ul_throughput_nr700},
                {"type": "LATENCY_PING", "value": rf.latency},
                {"type": "TX_IMBALANCE", "value": rf.tx_imbalance},
            ])

        # Add system metrics
        metrics_list.extend([
            {"type": "CPU_USAGE", "value": self.system_metrics.cpu_usage},
            {"type": "MEMORY_USAGE", "value": self.system_metrics.memory_usage},
            {"type": "TEMPERATURE", "value": self.system_metrics.temperature},
            {"type": "POWER_CONSUMPTION", "value": self.system_metrics.power_consumption},
        ])

        # Batch request format expected by the API
        # Use numeric station ID from database (station.id = 1)
        batch_request = {
            "stationId": 1,
            "metrics": metrics_list
        }

        try:
            async with self.session.post(metrics_url, json=batch_request, headers=headers) as resp:
                if resp.status in (200, 201):
                    logger.debug(f"Sent {len(metrics_list)} metrics")
                elif resp.status == 401:
                    logger.warning("Token expired, re-authenticating")
                    self.auth_token = None
                else:
                    body = await resp.text()
                    logger.warning(f"Failed to send metrics: {resp.status} - {body[:200]}")
        except Exception as e:
            logger.error(f"Error sending metrics: {e}")

    async def _send_heartbeat(self):
        """Send heartbeat to the platform."""
        heartbeat = {
            "stationId": self.station_id,
            "stationName": self.station_name,
            "state": self.state.value,
            "timestamp": datetime.now().isoformat(),
            "uptime": self.system_metrics.uptime,
            "issueActive": self.inject_issue is not None,
        }

        await self._broadcast_event("HEARTBEAT", heartbeat)

    async def _broadcast_metrics(self):
        """Broadcast metrics to all WebSocket clients."""
        if not self.ws_clients:
            return

        data = {
            "type": "METRICS",
            "timestamp": datetime.now().isoformat(),
            "stationId": self.station_id,
            "state": self.state.value,
            "sectors": {
                sector_id: asdict(metrics)
                for sector_id, metrics in self.rf_metrics.items()
            },
            "system": asdict(self.system_metrics),
            "issue": self.inject_issue,
            "issueSeverity": self.issue_severity,
        }

        message = json.dumps(data)

        for ws in self.ws_clients.copy():
            try:
                await ws.send_str(message)
            except Exception:
                self.ws_clients.discard(ws)

    async def _broadcast_event(self, event_type: str, data: dict):
        """Broadcast an event to all WebSocket clients."""
        if not self.ws_clients:
            return

        message = json.dumps({
            "type": event_type,
            "timestamp": datetime.now().isoformat(),
            **data
        })

        for ws in self.ws_clients.copy():
            try:
                await ws.send_str(message)
            except Exception:
                self.ws_clients.discard(ws)

    async def handle_command(self, command: str, params: dict = None) -> dict:
        """Handle a command from the platform."""
        params = params or {}
        logger.info(f"Received command: {command} with params: {params}")

        handler = self.command_handlers.get(command)
        if handler:
            result = await handler(params)
            await self._broadcast_event("COMMAND_RESULT", {
                "command": command,
                "params": params,
                "result": result,
            })
            return result
        else:
            return {"success": False, "error": f"Unknown command: {command}"}

    async def _handle_reboot(self, params: dict) -> dict:
        """Handle reboot command."""
        logger.info("Initiating reboot sequence...")
        self.state = StationState.MAINTENANCE
        await self._broadcast_event("STATE_CHANGE", {"newState": self.state.value})

        # Simulate reboot delay
        await asyncio.sleep(5)

        self.start_time = datetime.now()
        self.inject_issue = None
        self.issue_severity = 0.0
        self.state = StationState.ACTIVE

        await self._broadcast_event("STATE_CHANGE", {"newState": self.state.value})
        logger.info("Reboot complete")

        return {"success": True, "message": "Reboot completed"}

    async def _handle_set_power(self, params: dict) -> dict:
        """Handle set power command."""
        sector_id = params.get("sectorId", 1)
        power = params.get("power", 43.0)

        for sector in self.sectors:
            if sector.sector_id == sector_id:
                sector.tx_power = power
                logger.info(f"Sector {sector_id} TX power set to {power} dBm")
                return {"success": True, "sectorId": sector_id, "power": power}

        return {"success": False, "error": f"Sector {sector_id} not found"}

    async def _handle_set_tilt(self, params: dict) -> dict:
        """Handle set electrical tilt command."""
        sector_id = params.get("sectorId", 1)
        tilt = params.get("tilt", 4)

        for sector in self.sectors:
            if sector.sector_id == sector_id:
                sector.electrical_tilt = tilt
                logger.info(f"Sector {sector_id} electrical tilt set to {tilt} degrees")
                return {"success": True, "sectorId": sector_id, "tilt": tilt}

        return {"success": False, "error": f"Sector {sector_id} not found"}

    async def _handle_inject_issue(self, params: dict) -> dict:
        """Handle inject issue command (for testing)."""
        issue_type = params.get("issueType", "THROUGHPUT_DROP")
        severity = params.get("severity", 0.5)

        valid_issues = ['THROUGHPUT_DROP', 'TX_IMBALANCE', 'INTERFERENCE', 'HARDWARE_FAULT']
        if issue_type not in valid_issues:
            return {"success": False, "error": f"Invalid issue type. Valid: {valid_issues}"}

        self.inject_issue = issue_type
        self.issue_severity = max(0, min(1, severity))
        self.state = StationState.DEGRADED

        logger.warning(f"Issue injected: {issue_type} with severity {severity}")
        await self._broadcast_event("ISSUE_INJECTED", {
            "issueType": issue_type,
            "severity": self.issue_severity,
        })

        return {"success": True, "issueType": issue_type, "severity": self.issue_severity}

    async def _handle_clear_issue(self, params: dict) -> dict:
        """Handle clear issue command."""
        self.inject_issue = None
        self.issue_severity = 0.0
        self.state = StationState.ACTIVE

        logger.info("Issue cleared")
        await self._broadcast_event("ISSUE_CLEARED", {})

        return {"success": True, "message": "Issue cleared"}

    async def _handle_get_status(self, params: dict) -> dict:
        """Handle get status command."""
        return {
            "success": True,
            "stationId": self.station_id,
            "stationName": self.station_name,
            "state": self.state.value,
            "region": self.region,
            "city": self.city,
            "uptime": self.system_metrics.uptime,
            "sectors": len(self.sectors),
            "issueActive": self.inject_issue is not None,
            "issueType": self.inject_issue,
            "issueSeverity": self.issue_severity,
        }

    async def _handle_set_state(self, params: dict) -> dict:
        """Handle set state command."""
        new_state = params.get("state", "ACTIVE")
        try:
            self.state = StationState[new_state]
            await self._broadcast_event("STATE_CHANGE", {"newState": self.state.value})
            return {"success": True, "state": self.state.value}
        except KeyError:
            return {"success": False, "error": f"Invalid state: {new_state}"}


# HTTP Server for REST API and WebSocket
class StationServer:
    """HTTP server for the virtual station."""

    def __init__(self, station: Virtual5GStation, host: str = "0.0.0.0", port: int = 8090):
        self.station = station
        self.host = host
        self.port = port
        self.app = web.Application()
        self._setup_routes()

    def _setup_routes(self):
        self.app.router.add_get("/", self._handle_root)
        self.app.router.add_get("/health", self._handle_health)
        self.app.router.add_get("/status", self._handle_status)
        self.app.router.add_get("/metrics", self._handle_metrics)
        self.app.router.add_post("/command", self._handle_command)
        self.app.router.add_get("/ws", self._handle_websocket)

    async def _handle_root(self, request: web.Request) -> web.Response:
        return web.json_response({
            "service": "Virtual 5G Station",
            "stationId": self.station.station_id,
            "stationName": self.station.station_name,
            "state": self.station.state.value,
            "endpoints": ["/health", "/status", "/metrics", "/command", "/ws"]
        })

    async def _handle_health(self, request: web.Request) -> web.Response:
        return web.json_response({
            "status": "UP" if self.station.state != StationState.OFFLINE else "DOWN",
            "state": self.station.state.value
        })

    async def _handle_status(self, request: web.Request) -> web.Response:
        result = await self.station.handle_command("GET_STATUS")
        return web.json_response(result)

    async def _handle_metrics(self, request: web.Request) -> web.Response:
        return web.json_response({
            "stationId": self.station.station_id,
            "timestamp": datetime.now().isoformat(),
            "sectors": {
                sector_id: asdict(metrics)
                for sector_id, metrics in self.station.rf_metrics.items()
            },
            "system": asdict(self.station.system_metrics)
        })

    async def _handle_command(self, request: web.Request) -> web.Response:
        try:
            data = await request.json()
            command = data.get("command")
            params = data.get("params", {})

            if not command:
                return web.json_response(
                    {"success": False, "error": "Command required"},
                    status=400
                )

            result = await self.station.handle_command(command, params)
            return web.json_response(result)
        except Exception as e:
            return web.json_response(
                {"success": False, "error": str(e)},
                status=500
            )

    async def _handle_websocket(self, request: web.Request) -> web.WebSocketResponse:
        ws = web.WebSocketResponse()
        await ws.prepare(request)

        self.station.ws_clients.add(ws)
        logger.info(f"WebSocket client connected. Total: {len(self.station.ws_clients)}")

        try:
            async for msg in ws:
                if msg.type == WSMsgType.TEXT:
                    try:
                        data = json.loads(msg.data)
                        command = data.get("command")
                        params = data.get("params", {})

                        if command:
                            result = await self.station.handle_command(command, params)
                            await ws.send_json({"type": "COMMAND_RESPONSE", **result})
                    except json.JSONDecodeError:
                        await ws.send_json({"error": "Invalid JSON"})
                elif msg.type == WSMsgType.ERROR:
                    logger.error(f"WebSocket error: {ws.exception()}")
        finally:
            self.station.ws_clients.discard(ws)
            logger.info(f"WebSocket client disconnected. Total: {len(self.station.ws_clients)}")

        return ws

    async def start(self):
        runner = web.AppRunner(self.app)
        await runner.setup()
        site = web.TCPSite(runner, self.host, self.port)
        await site.start()
        logger.info(f"Station server running on http://{self.host}:{self.port}")


async def main():
    # Configuration from environment
    station_id = os.environ.get("STATION_ID", "VIRT-001")
    station_name = os.environ.get("STATION_NAME", "Virtual-5G-Station-001")
    api_gateway_url = os.environ.get("API_GATEWAY_URL", "http://api-gateway:8080")
    region = os.environ.get("STATION_REGION", "VIRTUAL")
    city = os.environ.get("STATION_CITY", "Simulation")
    port = int(os.environ.get("STATION_PORT", "8090"))

    # Create station and server
    station = Virtual5GStation(
        station_id=station_id,
        station_name=station_name,
        api_gateway_url=api_gateway_url,
        region=region,
        city=city
    )

    server = StationServer(station, port=port)

    # Handle shutdown
    loop = asyncio.get_event_loop()

    def shutdown_handler():
        logger.info("Shutdown signal received")
        asyncio.create_task(station.stop())

    for sig in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(sig, shutdown_handler)

    # Start everything
    await station.start()
    await server.start()

    # Keep running
    try:
        while station.running:
            await asyncio.sleep(1)
    except asyncio.CancelledError:
        pass
    finally:
        await station.stop()


if __name__ == "__main__":
    asyncio.run(main())
