#!/bin/bash
# Generate self-signed TLS certificates for device protocol testing.
#
# Creates:
#   certs/ca.crt, certs/ca.key          - Certificate Authority
#   certs/server.crt, certs/server.key  - Device-simulator server cert
#   certs/client.crt, certs/client.key  - Edge-bridge client cert (mutual TLS)
#
# Usage:
#   ./scripts/generate-tls-certs.sh [output-dir]
#   Default output: ./certs/

set -euo pipefail

CERT_DIR="${1:-./certs}"
DAYS=365
CA_SUBJECT="/CN=BaseStation-CA/O=BaseStation Platform/OU=Development"
SERVER_SUBJECT="/CN=device-simulator/O=BaseStation Platform/OU=Device"
CLIENT_SUBJECT="/CN=edge-bridge/O=BaseStation Platform/OU=Bridge"

mkdir -p "$CERT_DIR"

echo "=== Generating TLS certificates in $CERT_DIR ==="

# 1. CA key and certificate
echo "  [1/3] Generating CA..."
openssl genrsa -out "$CERT_DIR/ca.key" 4096 2>/dev/null
openssl req -new -x509 -key "$CERT_DIR/ca.key" -sha256 \
    -subj "$CA_SUBJECT" -days "$DAYS" -out "$CERT_DIR/ca.crt"

# 2. Server certificate (device-simulator)
echo "  [2/3] Generating server certificate..."
openssl genrsa -out "$CERT_DIR/server.key" 2048 2>/dev/null
openssl req -new -key "$CERT_DIR/server.key" -sha256 \
    -subj "$SERVER_SUBJECT" -out "$CERT_DIR/server.csr"

# SAN for docker service names and localhost
cat > "$CERT_DIR/server-ext.cnf" <<EOF
subjectAltName = DNS:device-simulator,DNS:device-sim-1,DNS:device-sim-2,DNS:device-sim-3,DNS:localhost,IP:127.0.0.1
EOF

openssl x509 -req -in "$CERT_DIR/server.csr" \
    -CA "$CERT_DIR/ca.crt" -CAkey "$CERT_DIR/ca.key" -CAcreateserial \
    -sha256 -days "$DAYS" -extfile "$CERT_DIR/server-ext.cnf" \
    -out "$CERT_DIR/server.crt" 2>/dev/null

# 3. Client certificate (edge-bridge, for mutual TLS)
echo "  [3/3] Generating client certificate..."
openssl genrsa -out "$CERT_DIR/client.key" 2048 2>/dev/null
openssl req -new -key "$CERT_DIR/client.key" -sha256 \
    -subj "$CLIENT_SUBJECT" -out "$CERT_DIR/client.csr"
openssl x509 -req -in "$CERT_DIR/client.csr" \
    -CA "$CERT_DIR/ca.crt" -CAkey "$CERT_DIR/ca.key" -CAcreateserial \
    -sha256 -days "$DAYS" -out "$CERT_DIR/client.crt" 2>/dev/null

# Cleanup CSR and temp files
rm -f "$CERT_DIR"/*.csr "$CERT_DIR"/*.cnf "$CERT_DIR"/*.srl

# Set permissions
chmod 644 "$CERT_DIR"/*.crt
chmod 600 "$CERT_DIR"/*.key

echo ""
echo "=== Certificates generated ==="
echo "  CA:     $CERT_DIR/ca.crt, $CERT_DIR/ca.key"
echo "  Server: $CERT_DIR/server.crt, $CERT_DIR/server.key"
echo "  Client: $CERT_DIR/client.crt, $CERT_DIR/client.key"
echo ""
echo "Usage:"
echo "  Device simulator (server):"
echo "    python mips_device.py --tls-cert $CERT_DIR/server.crt --tls-key $CERT_DIR/server.key --tls-ca $CERT_DIR/ca.crt"
echo ""
echo "  Edge bridge config (client):"
echo "    device:"
echo "      tcp:"
echo "        tls:"
echo "          enabled: true"
echo "          cert_file: $CERT_DIR/client.crt"
echo "          key_file: $CERT_DIR/client.key"
echo "          ca_file: $CERT_DIR/ca.crt"
