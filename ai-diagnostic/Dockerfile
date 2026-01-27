FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && \
    pip install --no-cache-dir flask requests

# Copy service code
COPY service/ ./service/
COPY virtual-basestation/ ./virtual-basestation/

# Expose ports
EXPOSE 9090 9091

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:9091/health || exit 1

# Run the service with HTTP enabled
CMD ["python", "service/diagnostic_service.py", "--tcp-port", "9090", "--http-port", "9091"]
