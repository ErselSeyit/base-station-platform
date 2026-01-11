# 🔴 CRITICAL: Next Steps After Secret Removal

## Immediate Actions Required

### ⚠️ ALL EXPOSED CREDENTIALS MUST BE ROTATED

The following credentials were exposed in Git history and **must be changed immediately**:

1. **PostgreSQL Passwords (3 databases)**
   - Base Station DB: `EGmwvjP56xV7iuKiC7Z8y4xgpz4XdhmT` ← COMPROMISED
   - Notification DB: `guCGykTnRBrYKTqeW9GShXMbP6iQ8dwe` ← COMPROMISED
   - Auth DB: `a2k3QeIpmymGwhksNP109KLvQmWE6tCb` ← COMPROMISED

2. **MongoDB**
   - Password: `NiatPcrSdcvGk2ubuWg3NIuFTWRmSLLy` ← COMPROMISED

3. **RabbitMQ**
   - Password: `FH7z3VLbbtQ6x0C1Y4q2V3jF7rtSR8nc` ← COMPROMISED

4. **JWT Secret**
   - Secret: `vY0DEl+VSvp0atTkJ+m+mh+a3fm2qAAiZ7ZMcJ1IrLKzxKe9U2HAsf6MPfTbYNzTnvAWBlJ7ldqKPvCsdMu1Ag==` ← COMPROMISED

5. **Internal Security Secret**
   - Secret: `d07cb7f8500aeb8a5a43028d12eadeb86b4a917bc51993e282e1ec80d10aca18` ← COMPROMISED

6. **Grafana**
   - Password: `dvb68mQHm8C04UTv9CxuehofiwL5ornM` ← COMPROMISED

---

## Step-by-Step Rotation Guide

### Step 1: Generate New Secrets

Run the secret generator script:

```bash
./scripts/generate-secrets.sh
```

This will output:
- New cryptographically secure secrets
- Commands to create Sealed Secrets for Kubernetes
- Environment variables for Docker Compose

**SAVE THE OUTPUT SECURELY** (password manager, encrypted file, etc.)

---

### Step 2A: For Kubernetes Deployment (Recommended)

Use Sealed Secrets to encrypt secrets before committing:

```bash
# 1. Verify Sealed Secrets controller is running
kubectl get pods -n kube-system | grep sealed-secrets

# 2. Create sealed secrets using output from generate-secrets.sh
# (Follow the kubectl commands in the script output)

# Example for JWT secret:
kubectl create secret generic jwt-secret \
  --namespace=basestation-platform \
  --dry-run=client \
  --from-literal=secret="<NEW_SECRET_FROM_SCRIPT>" \
  -o yaml | \
  kubeseal --format=yaml > k8s/sealed-secret-jwt.yaml

# 3. Apply sealed secrets
kubectl apply -f k8s/sealed-secret-*.yaml

# 4. Restart all services to pick up new secrets
kubectl rollout restart deployment -n basestation-platform

# 5. Verify services are healthy
kubectl get pods -n basestation-platform
kubectl logs -f deployment/auth-service -n basestation-platform
```

---

### Step 2B: For Docker Compose (Development)

Update the `.env` file with new secrets:

```bash
# 1. Backup existing .env
cp .env .env.backup

# 2. Copy the environment variables from generate-secrets.sh output
# and update .env file

# 3. Restart services
docker-compose down
docker-compose up -d

# 4. Verify services are healthy
docker-compose ps
docker-compose logs auth-service
```

---

### Step 3: Verify Rotation

Test authentication with new credentials:

```bash
# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Should return a JWT token

# Test authenticated endpoint
TOKEN="<token_from_above>"
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stations
```

---

### Step 4: Commit Sealed Secrets (Kubernetes only)

Sealed secrets are **encrypted** and safe to commit:

```bash
git add k8s/sealed-secret-*.yaml
git commit -m "security: Add encrypted sealed secrets (post-rotation)"
git push
```

---

## What Was Fixed

✅ **Removed from Git:**
- `k8s/secrets.yaml` (deleted from version control)

✅ **Added to .gitignore:**
```
k8s/secrets.yaml
k8s/*-secrets.yaml
!k8s/sealed-secrets.yaml
```

✅ **New Tools Created:**
- [scripts/generate-secrets.sh](scripts/generate-secrets.sh) - Secure secret generator
- [k8s/secrets.yaml.example](k8s/secrets.yaml.example) - Safe template
- [docs/SECRET_MANAGEMENT.md](docs/SECRET_MANAGEMENT.md) - Complete guide
- [SECURITY_AUDIT.md](SECURITY_AUDIT.md) - Full audit report

---

## Important Security Notes

### ⚠️ Git History Still Contains Old Secrets

Even though `k8s/secrets.yaml` is deleted, the **Git history permanently contains the old secrets**. This is why rotation is critical.

**If the repository is public:**
1. Consider making it private
2. Rotate credentials again after making private
3. Consider this a security incident

**If you need to completely remove from history (advanced):**
```bash
# WARNING: This rewrites Git history - coordinate with team
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch k8s/secrets.yaml" \
  --prune-empty --tag-name-filter cat -- --all

# Force push (requires team coordination)
git push origin --force --all
```

---

### 🔒 Enable Secret Scanning

Prevent this from happening again:

**GitHub:**
1. Go to: Settings → Code security and analysis
2. Enable: "Secret scanning"
3. Enable: "Push protection"

**Pre-commit Hook:**
```bash
# Install git-secrets
brew install git-secrets  # macOS
# or
sudo apt-get install git-secrets  # Ubuntu

# Configure for this repo
cd /path/to/base-station-platform
git secrets --install
git secrets --register-aws

# Add custom patterns
git secrets --add 'password\s*=\s*["\'][^"\']{8,}'
git secrets --add 'secret\s*:\s*[A-Za-z0-9+/=]{20,}'
```

---

## Status Checklist

Track your progress:

- [ ] Generated new secrets using `./scripts/generate-secrets.sh`
- [ ] Saved new secrets in secure password manager
- [ ] Created Sealed Secrets (Kubernetes) OR updated .env (Docker Compose)
- [ ] Applied new secrets to cluster/containers
- [ ] Restarted all services
- [ ] Verified authentication works with new credentials
- [ ] Committed sealed secrets to Git (Kubernetes only)
- [ ] Enabled GitHub secret scanning
- [ ] Installed pre-commit hook (git-secrets)
- [ ] Updated documentation with rotation date
- [ ] Set calendar reminder for next rotation (90 days)

---

## Need Help?

- 📖 **Full Guide**: [docs/SECRET_MANAGEMENT.md](docs/SECRET_MANAGEMENT.md)
- 🔍 **Security Audit**: [SECURITY_AUDIT.md](SECURITY_AUDIT.md)
- 🔧 **Technical Debt**: [docs/TECHNICAL_DEBT_ANALYSIS.md](docs/TECHNICAL_DEBT_ANALYSIS.md)

---

## Summary

**Status:** 🟡 Secrets removed from Git, rotation pending

**Next Action:** Run `./scripts/generate-secrets.sh` and follow the rotation steps above

**Timeline:** Complete rotation before any production deployment

**After Rotation:** 🟢 Production ready (with rotated credentials)
