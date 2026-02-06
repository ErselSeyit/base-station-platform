FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY virtual_station.py .

# Expose ports
EXPOSE 8090

# Run
CMD ["python", "virtual_station.py"]
