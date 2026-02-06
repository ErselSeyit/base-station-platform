FROM python:3.12-slim

WORKDIR /app

# Install dependencies
RUN pip install --no-cache-dir requests

# Copy simulator
COPY anomaly_simulator.py .

# Run simulator
CMD ["python", "-u", "anomaly_simulator.py"]
