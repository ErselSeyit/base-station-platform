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

# Copy protocol implementation and device simulator
COPY mips_device.py .

# Expose the binary protocol port
EXPOSE 9999

# Station ID configurable via environment
ENV STATION_ID=MIPS-BS-001

# Run the device simulator with proper protocol server
ENTRYPOINT ["python", "-u", "mips_device.py"]
CMD ["--port", "9999"]
