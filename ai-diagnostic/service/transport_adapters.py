"""
Transport adapters for the AI diagnostic service.

Each adapter accepts problems over one wire protocol (TCP, Serial/UART, MQTT),
hands them to the injected `on_problem` callback, and sends the resulting
`Solution` back the same way. `serial` and `paho-mqtt` are optional and imported
lazily; the corresponding adapter degrades to a no-op start when absent.
Extracted from diagnostic_service.py, which re-exports these names.
"""

import json
import logging
import socket
import threading
from abc import ABC, abstractmethod
from dataclasses import asdict
from typing import Any, Callable, List, Optional

from service.models import Problem, Solution

# Optional serial support
try:
    import serial
    import serial.tools.list_ports
    SERIAL_AVAILABLE = True
except ImportError:
    SERIAL_AVAILABLE = False

# Optional MQTT support
try:
    import paho.mqtt.client as mqtt
    MQTT_AVAILABLE = True
except ImportError:
    MQTT_AVAILABLE = False

logger = logging.getLogger(__name__)


class ProtocolAdapter(ABC):
    """Base class for communication protocol adapters"""

    def __init__(self, on_problem: Optional[Callable[[Problem], Solution]] = None):
        self.on_problem: Optional[Callable[[Problem], Solution]] = on_problem
        self.running = False

    @abstractmethod
    def start(self):
        """Start listening for problems"""
        pass

    @abstractmethod
    def stop(self):
        """Stop listening"""
        pass

    @abstractmethod
    def send_solution(self, solution: Solution, destination: Any):
        """Send solution back to device"""
        pass


class TCPAdapter(ProtocolAdapter):
    """TCP/IP Socket adapter for Ethernet communication"""

    def __init__(self, on_problem: Optional[Callable[[Problem], Solution]] = None, host: str = "0.0.0.0", port: int = 9090):
        super().__init__(on_problem)
        self.host = host
        self.port = port
        self.server = None

    def start(self):
        self.running = True
        self.server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server.bind((self.host, self.port))
        self.server.listen(5)

        logger.info(f"TCP adapter listening on {self.host}:{self.port}")

        def accept_loop():
            while self.running and self.server:
                try:
                    self.server.settimeout(1.0)
                    conn, addr = self.server.accept()
                    threading.Thread(target=self._handle_client, args=(conn, addr)).start()
                except socket.timeout:
                    continue
                except Exception as e:
                    if self.running:
                        logger.error(f"TCP accept error: {e}")

        threading.Thread(target=accept_loop, daemon=True).start()

    def _handle_client(self, conn, addr):
        logger.info(f"TCP connection from {addr}")
        try:
            data = b''
            while True:
                chunk = conn.recv(4096)
                if not chunk:
                    break
                data += chunk
                if b'\n' in data:
                    break

            if data:
                problem_data = json.loads(data.decode().strip())
                problem = Problem(**problem_data, source_protocol="tcp")

                if self.on_problem:
                    solution = self.on_problem(problem)
                    self.send_solution(solution, conn)

        except Exception as e:
            logger.error(f"TCP client error: {e}")
        finally:
            conn.close()

    def send_solution(self, solution: Solution, conn: socket.socket):
        try:
            response = json.dumps(asdict(solution)) + '\n'
            conn.sendall(response.encode())
        except Exception as e:
            logger.error(f"Failed to send solution: {e}")

    def stop(self):
        self.running = False
        if self.server:
            self.server.close()


class SerialAdapter(ProtocolAdapter):
    """Serial/UART adapter for RS-232, RS-485, USB Serial"""

    def __init__(self, on_problem: Optional[Callable[[Problem], Solution]] = None, port: str = "/dev/ttyUSB0",
                 baudrate: int = 115200, timeout: float = 1.0):
        super().__init__(on_problem)
        self.port = port
        self.baudrate = baudrate
        self.timeout = timeout
        self.serial_conn = None

    @staticmethod
    def list_ports() -> List[str]:
        """List available serial ports"""
        if not SERIAL_AVAILABLE:
            return []
        ports = serial.tools.list_ports.comports()
        return [p.device for p in ports]

    def _read_loop(self):
        """Read serial data in a loop and process complete lines."""
        buffer = ""
        while self.running and self.serial_conn:
            try:
                if self.serial_conn.in_waiting:
                    data = self.serial_conn.read(self.serial_conn.in_waiting).decode()
                    buffer = self._process_buffer(buffer + data)
            except Exception as e:
                logger.error(f"Serial read error: {e}")

    def _process_buffer(self, buffer: str) -> str:
        """Process buffer, handling complete lines and returning remainder."""
        while '\n' in buffer:
            line, buffer = buffer.split('\n', 1)
            if line.strip():
                self._process_message(line.strip())
        return buffer

    def start(self):
        if not SERIAL_AVAILABLE:
            logger.warning("Serial not available - install pyserial")
            return

        self.running = True
        try:
            self.serial_conn = serial.Serial(
                port=self.port,
                baudrate=self.baudrate,
                timeout=self.timeout
            )
            logger.info(f"Serial adapter connected to {self.port} at {self.baudrate} baud")
            threading.Thread(target=self._read_loop, daemon=True).start()

        except serial.SerialException as e:
            logger.error(f"Failed to open serial port {self.port}: {e}")
            logger.info(f"Available ports: {self.list_ports()}")

    def _process_message(self, message: str):
        try:
            problem_data = json.loads(message)
            problem = Problem(**problem_data, source_protocol="serial")
            if self.on_problem:
                solution = self.on_problem(problem)
                self.send_solution(solution, None)
        except json.JSONDecodeError:
            logger.warning(f"Invalid JSON from serial: {message[:100]}")

    def send_solution(self, solution: Solution, _):
        if self.serial_conn and self.serial_conn.is_open:
            response = json.dumps(asdict(solution)) + '\n'
            self.serial_conn.write(response.encode())

    def stop(self):
        self.running = False
        if self.serial_conn:
            self.serial_conn.close()


class MQTTAdapter(ProtocolAdapter):
    """MQTT adapter for IoT communication"""

    def __init__(self, on_problem: Optional[Callable[[Problem], Solution]] = None, broker: str = "localhost", port: int = 1883,
                 topic_problems: str = "basestation/+/problems",
                 topic_solutions: str = "basestation/{station_id}/solutions"):
        super().__init__(on_problem)
        self.broker = broker
        self.port = port
        self.topic_problems = topic_problems
        self.topic_solutions = topic_solutions
        self.client = None

    def start(self):
        if not MQTT_AVAILABLE:
            logger.warning("MQTT not available - install paho-mqtt")
            return

        self.running = True
        self.client = mqtt.Client()

        def on_connect(client, userdata, flags, rc):
            logger.info(f"MQTT connected to {self.broker}:{self.port}")
            client.subscribe(self.topic_problems)

        def on_message(client, userdata, msg):
            try:
                problem_data = json.loads(msg.payload.decode())
                problem = Problem(**problem_data, source_protocol="mqtt")
                if self.on_problem:
                    solution = self.on_problem(problem)
                    self.send_solution(solution, problem.station_id)
            except Exception as e:
                logger.error(f"MQTT message error: {e}")

        self.client.on_connect = on_connect
        self.client.on_message = on_message

        try:
            self.client.connect(self.broker, self.port)
            self.client.loop_start()
        except Exception as e:
            logger.error(f"MQTT connection failed: {e}")

    def send_solution(self, solution: Solution, station_id: str):
        if self.client:
            topic = self.topic_solutions.format(station_id=station_id)
            self.client.publish(topic, json.dumps(asdict(solution)))

    def stop(self):
        self.running = False
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()
