# MIPS Virtual Base Station Device Simulator
# Generates realistic 5G NR metrics for edge-bridge integration
#
# Metrics generated:
#   System: CPU, Memory, Temperature, Power
#   5G NR3500 (n78): DL/UL throughput, RSRP, SINR
#   5G NR700 (n28): DL/UL throughput, RSRP, SINR
#   Quality: Latency, TX Imbalance

FROM python:3.12-slim

WORKDIR /app

# No external dependencies - uses only stdlib
COPY mips_device.py .

# Expose the binary protocol port
EXPOSE 9999

# Station ID configurable via environment
ENV STATION_ID=MIPS-BS-001

# Run the device simulator
ENTRYPOINT ["sh", "-c", "python -u mips_device.py --station-id ${STATION_ID} --port 9999"]
