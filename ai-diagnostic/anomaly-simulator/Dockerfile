FROM python:3.12-slim

WORKDIR /app

# Install dependencies
RUN pip install --no-cache-dir requests flask

# Copy simulator
COPY anomaly_simulator.py .

# Run simulator
RUN useradd --create-home --shell /bin/bash app
USER app
CMD ["python", "-u", "anomaly_simulator.py"]
