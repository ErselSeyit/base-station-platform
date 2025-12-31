# Keycloak Integration Guide

This guide explains how to integrate Keycloak as an Identity Provider (IdP) to replace static JWT secrets with centralized OAuth2/OIDC authentication.

---

## Why Keycloak?

**Current State:** Static JWT secrets in environment variables
- No centralized user management
- No password rotation policies
- No SSO (Single Sign-On)
- Manual user provisioning

**With Keycloak:**
- ✅ Centralized identity management
- ✅ OAuth2/OIDC standard protocols
- ✅ SSO across multiple applications
- ✅ Password policies, MFA/2FA support
- ✅ User federation (LDAP, Active Directory)
- ✅ Fine-grained authorization

---

## Architecture Overview

```
┌─────────────┐
│   Frontend  │
└──────┬──────┘
       │ 1. OAuth2 Authorization Code Flow
       ▼
┌─────────────────┐         ┌──────────────┐
│  API Gateway    │────────▶│  Keycloak    │
└──────┬──────────┘         │  (IdP)       │
       │ 2. JWT Validation  └──────────────┘
       │    (JWK Set)              │
       ▼                           │ User DB
┌──────────────────┐               │
│  Microservices   │               ▼
│  - Base Station  │         ┌──────────────┐
│  - Monitoring    │         │ PostgreSQL   │
│  - Notification  │         │ (auth-service│
└──────────────────┘         │  tables)     │
                             └──────────────┘
```

---

## Quick Start

### 1. Start Keycloak

Keycloak is already configured in `docker-compose.yml`:

```bash
docker compose up -d keycloak
```

**Access Keycloak Admin Console:**
- URL: http://localhost:8090
- Username: `admin`
- Password: `admin` (change in production!)

### 2. Verify Realm Import

The `base-station-platform` realm is automatically imported from `keycloak/realm-export.json`:

- **Realm**: base-station-platform
- **Client**: base-station-client
- **Users**: admin, user (same credentials as before)
- **Roles**: ROLE_ADMIN, ROLE_USER, ROLE_OPERATOR

### 3. Test OAuth2 Endpoints

```bash
# Get access token using password grant (for testing)
curl -X POST http://localhost:8090/realms/base-station-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=base-station-client" \
  -d "client_secret=base-station-secret-change-in-production" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin"

# Response includes:
# - access_token (JWT)
# - refresh_token
# - expires_in
# - token_type: Bearer
```

---

## Implementation Steps

### Step 1: Update docker-compose.yml

✅ **Already Done** - Keycloak service is configured:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26.0
  container_name: keycloak
  environment:
    KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN:-admin}
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-admin}
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://postgres-auth:5432/authdb
  ports:
    - "8090:8080"
  volumes:
    - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json
```

### Step 2: Update API Gateway JWT Validation

Update `api-gateway/src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/base-station-platform
          jwk-set-uri: http://keycloak:8080/realms/base-station-platform/protocol/openid-connect/certs
```

Update `JwtValidator.java` to validate Keycloak tokens:

```java
@Component
public class JwtValidator {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    private JwtDecoder jwtDecoder;

    @PostConstruct
    public void init() {
        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    public ValidationResult validateToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);

            String username = jwt.getClaimAsString("preferred_username");
            List<String> roles = jwt.getClaimAsStringList("roles");
            String role = roles != null && !roles.isEmpty() ? roles.get(0) : "ROLE_USER";

            return ValidationResult.valid(username, role);
        } catch (JwtException e) {
            return ValidationResult.invalid("Invalid Keycloak token: " + e.getMessage());
        }
    }
}
```

### Step 3: Update Frontend OAuth2 Flow

#### Option A: Authorization Code Flow (Recommended)

Update `frontend/src/services/authService.ts`:

```typescript
export class AuthService {
  private keycloakUrl = 'http://localhost:8090/realms/base-station-platform';
  private clientId = 'base-station-client';
  private redirectUri = 'http://localhost:3000/callback';

  // Redirect to Keycloak login
  login() {
    const authUrl = `${this.keycloakUrl}/protocol/openid-connect/auth?` +
      `client_id=${this.clientId}&` +
      `redirect_uri=${encodeURIComponent(this.redirectUri)}&` +
      `response_type=code&` +
      `scope=openid profile email`;

    window.location.href = authUrl;
  }

  // Handle OAuth2 callback
  async handleCallback(code: string) {
    const response = await fetch(`${this.keycloakUrl}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: this.clientId,
        client_secret: 'base-station-secret-change-in-production',
        code: code,
        redirect_uri: this.redirectUri
      })
    });

    const tokens = await response.json();
    localStorage.setItem('access_token', tokens.access_token);
    localStorage.setItem('refresh_token', tokens.refresh_token);
    return tokens;
  }

  // Refresh access token
  async refreshToken() {
    const refreshToken = localStorage.getItem('refresh_token');
    const response = await fetch(`${this.keycloakUrl}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: this.clientId,
        client_secret: 'base-station-secret-change-in-production',
        refresh_token: refreshToken
      })
    });

    const tokens = await response.json();
    localStorage.setItem('access_token', tokens.access_token);
    return tokens.access_token;
  }
}
```

#### Option B: Use Keycloak JS Adapter

```bash
npm install keycloak-js
```

```typescript
import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'http://localhost:8090',
  realm: 'base-station-platform',
  clientId: 'base-station-client'
});

// Initialize Keycloak
keycloak.init({ onLoad: 'login-required' }).then(authenticated => {
  if (authenticated) {
    console.log('User authenticated');
    // Use keycloak.token for API requests
  }
});

// Auto-refresh token
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30);
};
```

### Step 4: Update Downstream Services

Each microservice should validate Keycloak JWTs.

Update `pom.xml` for all services:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Update `application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/base-station-platform
```

Update Security Configuration:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }
}
```

---

## Configuration Reference

### Environment Variables

Add to `.env`:

```bash
# Keycloak Admin
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<strong-password-here>

# Keycloak URLs (internal Docker network)
KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/base-station-platform
KEYCLOAK_JWK_SET_URI=http://keycloak:8080/realms/base-station-platform/protocol/openid-connect/certs

# OAuth2 Client Credentials
KEYCLOAK_CLIENT_ID=base-station-client
KEYCLOAK_CLIENT_SECRET=<generate-secure-secret>

# Authentication Mode Toggle
AUTH_MODE=keycloak  # or "jwt" for legacy custom JWT
```

### Realm Configuration

The realm is defined in `keycloak/realm-export.json`:

```json
{
  "realm": "base-station-platform",
  "enabled": true,
  "users": [
    {
      "username": "admin",
      "email": "admin@basestation.local",
      "credentials": [{"type": "password", "value": "admin"}],
      "realmRoles": ["ROLE_ADMIN"]
    }
  ],
  "clients": [
    {
      "clientId": "base-station-client",
      "secret": "base-station-secret-change-in-production",
      "redirectUris": ["http://localhost:3000/*"],
      "webOrigins": ["http://localhost:3000"]
    }
  ]
}
```

---

## Migration Strategy

### Phase 1: Parallel Mode (Current)

Both authentication methods work simultaneously:

```yaml
auth:
  mode: jwt  # Use legacy JWT for now
```

- Keycloak runs alongside custom auth-service
- No breaking changes to existing flows
- Test Keycloak independently

### Phase 2: Gradual Migration

1. **Week 1:** Deploy Keycloak, import users
2. **Week 2:** Update frontend to support both flows
3. **Week 3:** Migrate API Gateway to Keycloak validation
4. **Week 4:** Update all microservices
5. **Week 5:** Full testing, rollback plan ready

### Phase 3: Keycloak Only

```yaml
auth:
  mode: keycloak  # Fully migrated
```

- Decommission custom auth-service JWT generation
- Keep database for user sync/backup

---

## Testing

### Test Token Generation

```bash
# Get access token
TOKEN=$(curl -s -X POST http://localhost:8090/realms/base-station-platform/protocol/openid-connect/token \
  -d "client_id=base-station-client" \
  -d "client_secret=base-station-secret-change-in-production" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin" | jq -r '.access_token')

# Test API call with Keycloak token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/stations
```

### Decode JWT Token

```bash
echo $TOKEN | cut -d '.' -f 2 | base64 -d | jq
```

Expected claims:
```json
{
  "exp": 1234567890,
  "iat": 1234567890,
  "sub": "user-uuid",
  "preferred_username": "admin",
  "email": "admin@basestation.local",
  "roles": ["ROLE_ADMIN"],
  "iss": "http://localhost:8090/realms/base-station-platform"
}
```

---

## Production Considerations

### 1. Use HTTPS

```yaml
KC_HOSTNAME: keycloak.yourcompany.com
KC_HTTPS_CERTIFICATE_FILE: /opt/keycloak/certs/cert.pem
KC_HTTPS_CERTIFICATE_KEY_FILE: /opt/keycloak/certs/key.pem
```

### 2. External Database

Use dedicated PostgreSQL (not shared with auth-service):

```yaml
KC_DB_URL: jdbc:postgresql://keycloak-db:5432/keycloak
```

### 3. Clustering (High Availability)

Deploy multiple Keycloak instances with shared database and Infinispan cache.

### 4. Custom Themes

Brand the login page:

```
keycloak/themes/base-station/
  ├── login/
  │   ├── theme.properties
  │   ├── login.ftl
  │   └── resources/css/login.css
```

### 5. User Federation

Integrate with corporate LDAP/Active Directory:

- Keycloak Admin Console → User Federation → Add LDAP provider
- Configure LDAP connection, user/group mappings
- Enable user sync

---

## Troubleshooting

### Keycloak Won't Start

```bash
docker logs keycloak
# Check for database connection errors
```

### Token Validation Fails

1. Verify JWK Set URL is accessible:
```bash
curl http://keycloak:8080/realms/base-station-platform/protocol/openid-connect/certs
```

2. Check issuer matches exactly:
```bash
# Token iss claim must match issuer-uri in application.yml
```

### CORS Errors

Update Keycloak client `webOrigins`:
```json
"webOrigins": ["http://localhost:3000", "http://frontend"]
```

---

## Benefits Summary

| Feature | Custom JWT | Keycloak OAuth2 |
|---------|-----------|-----------------|
| User Management | Manual DB | Centralized UI |
| Password Policies | None | Configurable |
| MFA/2FA | ❌ Not supported | ✅ Built-in |
| SSO | ❌ Not supported | ✅ Supported |
| Token Rotation | ❌ Manual | ✅ Automatic |
| User Federation | ❌ Not supported | ✅ LDAP/AD |
| Audit Logs | Custom logging | ✅ Built-in |
| Standards | Custom | ✅ OAuth2/OIDC |

---

## Next Steps

1. **Start Keycloak**: `docker compose up -d keycloak`
2. **Access Admin Console**: http://localhost:8090
3. **Review realm configuration**: Check users, clients, roles
4. **Test token generation**: Use curl to get access tokens
5. **Plan migration**: Follow the phased approach above

For questions, see:
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
