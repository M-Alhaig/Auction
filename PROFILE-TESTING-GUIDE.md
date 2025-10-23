# Profile-Based Endpoint Restriction Testing Guide

## Overview

The manual auction lifecycle endpoints (`/start` and `/end`) are now protected by Spring profiles to prevent accidental use in production environments.

## Protected Endpoints

These endpoints are **disabled in production** and only available in development/test environments:

1. **`PATCH /api/items/{id}/start`** - Manually start an auction (PENDING → ACTIVE)
2. **`PATCH /api/items/{id}/end`** - Manually end an auction (ACTIVE → ENDED)

## Implementation Details

**Annotation Used:** `@Profile("!production")`

**Behavior:**
- ✅ **Available**: When profile is `dev`, `test`, `staging`, or no profile set (default)
- ❌ **Disabled**: When profile is `production`

## Testing Instructions

### 1. Default Behavior (No Profile = Endpoints Available)

```bash
# Start Item Service normally
cd item-service
mvn spring-boot:run

# The endpoints will be available
# You should see in logs: "Mapping PATCH /api/items/{id}/start"
```

**Expected Result:** Endpoints are **registered and accessible**.

---

### 2. Development Profile (Endpoints Available)

```bash
# Start with dev profile
cd item-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# OR using environment variable
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

**Expected Result:** Endpoints are **registered and accessible**.

---

### 3. Production Profile (Endpoints Disabled)

```bash
# Start with production profile
cd item-service
mvn spring-boot:run -Dspring-boot.run.profiles=production

# OR using environment variable
export SPRING_PROFILES_ACTIVE=production
mvn spring-boot:run
```

**Expected Behavior:**
- ✅ The controller bean is created, but methods with `@Profile("!production")` are **NOT registered**
- ❌ Requests to `/api/items/{id}/start` or `/api/items/{id}/end` will return **404 Not Found**
- ✅ All other endpoints remain functional

**Log Evidence:**
```
# In startup logs, you should see mapping for other endpoints:
Mapped "{[/api/items/{id}],methods=[GET]}" onto public ResponseEntity<ItemResponse> ...
Mapped "{[/api/items],methods=[POST]}" onto public ResponseEntity<ItemResponse> ...

# But NOT these:
# MISSING: Mapped "{[/api/items/{id}/start],methods=[PATCH]}" (NOT registered!)
# MISSING: Mapped "{[/api/items/{id}/end],methods=[PATCH]}" (NOT registered!)
```

---

## Verification Using Postman

### Test 1: Development Environment (Default)

1. Start service normally: `mvn spring-boot:run`
2. In Postman, send request to `PATCH http://localhost:8082/api/items/1/start`
3. **Expected:** 200 OK (or appropriate business logic response)

### Test 2: Production Environment

1. Stop the service
2. Restart with production profile: `mvn spring-boot:run -Dspring-boot.run.profiles=production`
3. In Postman, send request to `PATCH http://localhost:8082/api/items/1/start`
4. **Expected:** 404 Not Found with message like:
   ```json
   {
     "timestamp": "2025-10-23T10:30:00.000+00:00",
     "status": 404,
     "error": "Not Found",
     "path": "/api/items/1/start"
   }
   ```

### Test 3: Verify Other Endpoints Still Work in Production

1. With production profile active, send: `GET http://localhost:8082/api/items/1`
2. **Expected:** 200 OK with item details

---

## Configuration File Approach

You can also set the profile in `application.properties`:

### For Development (Default)
```properties
# item-service/src/main/resources/application.properties
# No profile specified = endpoints available
```

### For Production
```properties
# item-service/src/main/resources/application-production.properties
spring.profiles.active=production
```

Then start with:
```bash
java -jar item-service.jar --spring.profiles.active=production
```

---

## Why This Matters

### Security Benefits
- **Prevents accidental manual intervention** in live auctions
- **Forces production to use scheduler-based lifecycle** (proper workflow)
- **Maintains audit trail integrity** (all lifecycle changes are time-based, not manual)

### Development Benefits
- **Enables rapid testing** without waiting for scheduled times
- **Allows manual control in staging/test environments**
- **Debugging auction flows** during development

---

## Interview Talking Points

**Q: How do you prevent dangerous operations in production?**

> "I use Spring's `@Profile` annotations to conditionally enable/disable endpoints based on the active profile. For example, manual auction lifecycle endpoints are annotated with `@Profile('!production')`, which means they're only registered when the profile is NOT production. This is a compile-time safety mechanism that completely removes the endpoints from the routing table in production environments."

**Q: What's the difference between authorization and profile-based restrictions?**

> "Authorization controls *who* can access an endpoint (e.g., only ADMIN role), while profile-based restrictions control *when* an endpoint exists at all. Profile restrictions are environment-aware and prevent entire features from being available in certain deployments. This is useful for test-only operations, debug endpoints, or staged feature rollouts."

---

## Additional Notes

- **Scheduler is always active:** The automated auction lifecycle scheduler runs in all environments
- **Profile-aware logging:** Consider adding profile-specific log levels in `application-{profile}.properties`
- **Multiple profiles:** You can combine profiles: `-Dspring-boot.run.profiles=dev,local`

---

## Quick Reference

| Profile | Manual Start/End Available? | Use Case |
|---------|---------------------------|----------|
| (none) | ✅ Yes | Local development |
| `dev` | ✅ Yes | Development environment |
| `test` | ✅ Yes | Integration testing |
| `staging` | ✅ Yes | Pre-production testing |
| `production` | ❌ **NO** | Live production |

---

**Testing Checklist:**
- [ ] Verify endpoints work in default mode
- [ ] Verify endpoints work with `dev` profile
- [ ] Verify endpoints return 404 with `production` profile
- [ ] Verify all other CRUD endpoints work in production
- [ ] Check startup logs for endpoint registration differences
