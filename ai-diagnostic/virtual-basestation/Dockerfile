# MIPS Virtual Base Station Device Simulator
# Implements device protocol for edge-bridge connectivity
# Generates dynamic 5G NR metrics that flow to the monitoring service
#
# Metrics generated:
#   System: CPU, Memory, Temperature, Power, Fan Speed
#   5G NR3500 (n78): DL/UL throughput, RSRP, SINR
#   5G NR700 (n28): DL/UL throughput, RSRP, SINR
#   Radio: PDCP/RLC throughput, BLER, MCS, RB, Rank
#   Quality: Latency, TX Imbalance, Handover Success Rate

FROM python:3.12-slim

WORKDIR /app

# Install Flask for control API
RUN pip install --no-cache-dir flask

# Copy protocol implementation and device simulator
COPY mips_device.py .

# Expose the binary protocol port
EXPOSE 9999

# Configurable via environment
ENV STATION_ID=MIPS-BS-001
ENV DEVICE_PORT=9999
ENV CONTROL_PORT=8098
# TLS (optional): set TLS_CERT and TLS_KEY to enable
ENV TLS_CERT=""
ENV TLS_KEY=""
ENV TLS_CA=""

# Run the device simulator with proper protocol server
# Shell form to expand environment variables; TLS args omitted when empty
CMD python -u mips_device.py --station-id "$STATION_ID" --port "$DEVICE_PORT" --control-port "$CONTROL_PORT" \
    ${TLS_CERT:+--tls-cert "$TLS_CERT"} ${TLS_KEY:+--tls-key "$TLS_KEY"} ${TLS_CA:+--tls-ca "$TLS_CA"}
