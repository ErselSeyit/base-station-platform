FROM python:3.12-slim

WORKDIR /app

# Install dependencies
RUN pip install --no-cache-dir requests

# Copy protocol and simulator
COPY device_protocol.py .
COPY anomaly_simulator.py .

# Run simulator
CMD ["python", "-u", "anomaly_simulator.py"]
