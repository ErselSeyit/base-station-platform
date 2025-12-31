# Keycloak Configuration

This directory contains Keycloak realm configuration for the Base Station Platform.

## Files

- `realm-export.json`: Pre-configured realm with users, roles, and OAuth2 client

## Realm Overview

**Realm Name:** `base-station-platform`

### Users

| Username | Password | Role | Email |
|----------|----------|------|-------|
| admin | admin | ROLE_ADMIN | admin@basestation.local |
| user | user | ROLE_USER | user@basestation.local |

⚠️ **SECURITY WARNING:** Change these passwords in production!

### OAuth2 Client

- **Client ID:** `base-station-client`
- **Client Secret:** `base-station-secret-change-in-production`
- **Grant Types:** Authorization Code, Direct Access (Password)
- **Redirect URIs:** `http://localhost:3000/*`, `http://frontend/*`
- **Web Origins:** `http://localhost:3000`, `http://frontend`

### Roles

- `ROLE_ADMIN`: Full system access
- `ROLE_USER`: Standard user access
- `ROLE_OPERATOR`: Base station management access

## Usage

Keycloak automatically imports this realm on first startup using the `--import-realm` flag in docker-compose.yml.

### Start Keycloak

```bash
docker compose up -d keycloak
```

### Access Admin Console

- URL: http://localhost:8090
- Username: `admin`
- Password: `admin` (from KEYCLOAK_ADMIN env var)

### Get Access Token (Testing)

```bash
curl -X POST http://localhost:8090/realms/base-station-platform/protocol/openid-connect/token \
  -d "client_id=base-station-client" \
  -d "client_secret=base-station-secret-change-in-production" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" \
  | jq -r '.access_token'
```

## Customization

### Add New Users

1. Log into Keycloak Admin Console
2. Navigate to: Realm Settings → Users → Add user
3. Set username, email, and credentials
4. Assign roles: Role Mappings → Assign role

### Modify Client Settings

1. Navigate to: Clients → base-station-client
2. Update redirect URIs, secrets, or grant types
3. Save changes

### Export Modified Realm

```bash
docker exec -it keycloak /opt/keycloak/bin/kc.sh export \
  --dir /tmp \
  --realm base-station-platform \
  --users realm_file

docker cp keycloak:/tmp/base-station-platform-realm.json ./keycloak/realm-export.json
```

## Production Considerations

1. **Change Default Passwords:**
   - Set strong KEYCLOAK_ADMIN_PASSWORD
   - Update user credentials
   - Rotate client secrets

2. **Use HTTPS:**
   - Configure KC_HOSTNAME and SSL certificates
   - Update redirect URIs to use https://

3. **Dedicated Database:**
   - Use separate PostgreSQL instance for Keycloak
   - Don't share with auth-service database

4. **User Federation:**
   - Integrate LDAP/Active Directory for corporate users
   - Configure user sync and group mappings

5. **Enable MFA:**
   - Configure OTP/TOTP for admin users
   - Enable required actions for user enrollment

## See Also

- [Complete Integration Guide](../docs/KEYCLOAK_INTEGRATION.md)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [OAuth2/OIDC Specs](https://oauth.net/2/)
