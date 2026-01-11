# Secret Management Guide

This document explains how to manage secrets securely for the Base Station Platform.

---

## Overview

The platform requires several secrets for production deployment:

- **JWT Secret**: Used for signing authentication tokens
- **Internal Security Secret**: Used for service-to-service authentication
- **Database Passwords**: PostgreSQL (3 databases), MongoDB
- **Message Queue**: RabbitMQ credentials
- **Monitoring**: Grafana admin password

---

## 🔴 NEVER Commit Secrets to Git

**Why?**
- Git history is permanent - secrets can't be truly deleted
- Repository forks/clones expose secrets
- Public repositories expose secrets to the world
- Backup systems may store secrets

**What NOT to commit:**
- `k8s/secrets.yaml` - Kubernetes plaintext secrets
- `.env` - Local environment variables (already ignored)
- Any file containing passwords, API keys, or tokens

**What IS safe to commit:**
- `k8s/secrets.yaml.example` - Template with placeholder values
- `k8s/sealed-secret-*.yaml` - Encrypted Sealed Secrets
- `.env.example` - Template for environment variables

---

## Secret Management Strategies

### Option 1: Sealed Secrets (Recommended for Kubernetes)

Sealed Secrets encrypt secrets before committing to Git. Only the Kubernetes cluster can decrypt them.

**Advantages:**
- Encrypted secrets can be safely committed to Git
- GitOps-friendly (infrastructure as code)
- Automatic decryption in the cluster
- No external dependencies

**Setup:**

1. **Verify Sealed Secrets controller is installed:**
   ```bash
   kubectl get pods -n kube-system | grep sealed-secrets
   # Should show: sealed-secrets-controller-xxx   1/1   Running
   ```

2. **Generate random secrets:**
   ```bash
   ./scripts/generate-secrets.sh
   ```

3. **Create sealed secrets** (example for JWT):
   ```bash
   kubectl create secret generic jwt-secret \
     --namespace=basestation-platform \
     --dry-run=client \
     --from-literal=secret="YOUR_GENERATED_SECRET_HERE" \
     -o yaml | \
     kubeseal --format=yaml > k8s/sealed-secret-jwt.yaml
   ```

4. **Apply to cluster:**
   ```bash
   kubectl apply -f k8s/sealed-secret-jwt.yaml
   ```

5. **Commit the sealed secret** (encrypted, safe to commit):
   ```bash
   git add k8s/sealed-secret-jwt.yaml
   git commit -m "Add encrypted JWT secret"
   ```

**How it works:**
```
Plaintext Secret → Sealed Secrets Controller → Encrypted SealedSecret
                                                 (safe to commit)
                   ↓
                   Kubernetes cluster decrypts → Regular Secret
```

---

### Option 2: External Secret Managers

For production environments, consider using external secret management:

#### HashiCorp Vault
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "basestation-platform"
    vault.hashicorp.com/agent-inject-secret-jwt: "secret/data/jwt"
```

#### AWS Secrets Manager (EKS)
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: jwt-secret
spec:
  secretStoreRef:
    name: aws-secrets-manager
  target:
    name: jwt-secret
  data:
  - secretKey: secret
    remoteRef:
      key: /basestation/jwt-secret
```

#### Azure Key Vault (AKS)
```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: jwt-secret
spec:
  provider: azure
  parameters:
    keyvaultName: "basestation-keyvault"
    objects: |
      array:
        - objectName: "jwt-secret"
          objectType: "secret"
```

---

### Option 3: Docker Compose (Development Only)

For local development with Docker Compose, use `.env` file:

1. **Copy the example:**
   ```bash
   cp .env.example .env
   ```

2. **Generate secure secrets:**
   ```bash
   ./scripts/generate-secrets.sh
   ```

3. **Copy the generated values to `.env`**

4. **Verify `.env` is in `.gitignore`:**
   ```bash
   git check-ignore .env
   # Should output: .env
   ```

**⚠️ NEVER use development secrets in production!**

---

## Secret Rotation

Secrets should be rotated periodically:

### Rotation Schedule (Recommended)

- **JWT Secret**: Every 90 days
- **Internal Security Secret**: Every 90 days
- **Database Passwords**: Every 180 days
- **Service Accounts**: Every 90 days

### Rotation Procedure

1. **Generate new secret:**
   ```bash
   NEW_SECRET=$(openssl rand -base64 64 | tr -d '\n')
   ```

2. **Update the secret in Kubernetes:**
   ```bash
   kubectl create secret generic jwt-secret \
     --namespace=basestation-platform \
     --from-literal=secret="$NEW_SECRET" \
     --dry-run=client -o yaml | \
     kubectl apply -f -
   ```

3. **Rolling restart of affected services:**
   ```bash
   kubectl rollout restart deployment/auth-service -n basestation-platform
   kubectl rollout restart deployment/api-gateway -n basestation-platform
   ```

4. **Verify services are healthy:**
   ```bash
   kubectl get pods -n basestation-platform
   kubectl logs -f deployment/auth-service -n basestation-platform
   ```

---

## Secret Requirements

### JWT Secret
- **Format**: Base64-encoded random bytes
- **Minimum Length**: 256 bits (32 bytes)
- **Recommended**: 512 bits (64 bytes)
- **Generation**: `openssl rand -base64 64`

### Internal Security Secret
- **Format**: Hexadecimal string
- **Minimum Length**: 256 bits (32 bytes)
- **Generation**: `openssl rand -hex 32`

### Database Passwords
- **Format**: Base64-encoded random bytes
- **Minimum Length**: 128 bits (16 bytes)
- **Recommended**: 192 bits (24 bytes)
- **Generation**: `openssl rand -base64 24`

---

## Emergency: Secrets Leaked to Git

If secrets were accidentally committed:

### Immediate Actions

1. **Remove from Git:**
   ```bash
   git rm k8s/secrets.yaml
   git commit -m "security: Remove exposed secrets"
   git push
   ```

2. **Add to .gitignore:**
   ```bash
   echo "k8s/secrets.yaml" >> .gitignore
   git add .gitignore
   git commit -m "Prevent future secret commits"
   ```

3. **Rotate ALL exposed credentials immediately:**
   ```bash
   ./scripts/generate-secrets.sh
   # Follow the output to create new sealed secrets
   ```

4. **Update production clusters:**
   ```bash
   # Apply new sealed secrets
   kubectl apply -f k8s/sealed-secret-*.yaml

   # Restart all services
   kubectl rollout restart deployment -n basestation-platform
   ```

### Long-term Actions

5. **Consider Git history as compromised** (secrets are in history forever)

6. **If repository is public**, rotate again after repository becomes private

7. **Enable Git secret scanning:**
   - GitHub: Settings → Code security → Secret scanning
   - GitLab: Security & Compliance → Secret Detection
   - Pre-commit hooks: [git-secrets](https://github.com/awslabs/git-secrets)

---

## Automated Secret Scanning

### Pre-commit Hook (Recommended)

Install [git-secrets](https://github.com/awslabs/git-secrets):

```bash
# Install
brew install git-secrets  # macOS
# or
git clone https://github.com/awslabs/git-secrets.git
cd git-secrets && make install

# Configure for this repo
cd /path/to/base-station-platform
git secrets --install
git secrets --register-aws

# Add custom patterns
git secrets --add 'password\s*=\s*["\'][^"\']{8,}'
git secrets --add 'secret\s*=\s*["\'][^"\']{20,}'
git secrets --add 'api[_-]?key\s*=\s*["\'][^"\']{10,}'
```

### CI/CD Secret Scanning

Add to `.github/workflows/security.yml`:

```yaml
name: Security Scan
on: [push, pull_request]

jobs:
  secret-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0  # Full history for scanning

      - name: Run TruffleHog
        uses: trufflesecurity/trufflehog@main
        with:
          path: ./
          base: ${{ github.event.repository.default_branch }}
          head: HEAD
```

---

## Checklist

Before deploying to production:

- [ ] All secrets generated with cryptographically secure random generator
- [ ] No plaintext secrets committed to Git
- [ ] `.env` file in `.gitignore`
- [ ] `k8s/secrets.yaml` in `.gitignore`
- [ ] Sealed Secrets or external secret manager configured
- [ ] Secrets meet minimum length requirements
- [ ] Development secrets differ from production
- [ ] Secret rotation schedule documented
- [ ] Monitoring alerts for failed authentication (potential compromised secrets)
- [ ] Git secret scanning enabled
- [ ] Access to production secrets restricted (RBAC)

---

## References

- [Sealed Secrets Documentation](https://sealed-secrets.netlify.app/)
- [Kubernetes Secrets Best Practices](https://kubernetes.io/docs/concepts/configuration/secret/)
- [OWASP Secret Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [12 Factor App - Config](https://12factor.net/config)
