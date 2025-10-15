# Redis Usage Guide

## Dependency Overview
- `spring-boot-starter-data-redis` supplies Spring Data Redis integration and the Spring Cache abstraction.
- `io.lettuce:lettuce-core` is the chosen Redis driver (also pulled transitively, kept explicitly in the `pom.xml`).

## Configuration Entry Points
- `src/main/java/dev/ouanu/iems/config/RedisConfig.java`
  - Activates caching via `@EnableCaching`.
  - Builds a `LettuceConnectionFactory` targeting host/port/password resolved from `spring.data.redis.*` properties.
  - Exposes a `RedisTemplate<String, Object>` with `StringRedisSerializer` for keys/hash keys and `GenericJackson2JsonRedisSerializer` for values/hash values.
  - Provides a `RedisCacheManager` with 60-minute TTL and `null` value caching disabled.
- `src/main/resources/application-*.yml`
  - Each profile (`dev`, `test`, `prod`) supplies `spring.data.redis.host`, `port`, `password`, and basic Lettuce pool sizing.
  - Connection details are wired through environment variables (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`).
- `.env.dev` / `.env.test`
  - Default values for the Redis container: host alias `redis`, exposed port `6379`, and sample passwords per environment.
- `docker-compose.yml`
  - Declares a `redis:7-alpine` service named `iems_redis`, protects it with `--requirepass ${REDIS_PASSWORD}`, and publishes `${REDIS_PORT}:6379`.

## Runtime Beans & Serialization
- `RedisConnectionFactory`: single-node configuration, password always set (empty string if unspecified).
- `RedisTemplate<String, Object>`: prefer this bean for manual Redis operations; writes JSON payloads that remain language-agnostic.
- `RedisCacheManager`: backs Spring Cache; TTL can be adjusted in `RedisConfig` (default 60 minutes) if caching policy changes.

## Business Usage

### 1. Token Blacklist & Refresh Tracking (`RedisTokenService`)
- Location: `src/main/java/dev/ouanu/iems/service/RedisTokenService.java`.
- Responsibilities:
  - `blacklistAccessToken(jti, expiresAt)`: stores `blacklist:access:<jti>` with a TTL matching token expiry (fallback 5 minutes if already expired).
  - `storeRefreshToken(hash, ownerMarker, expiresAt)`: writes `refresh:<hash>` with TTL from refresh expiry (fallback 5 minutes).
  - `isTokenBlacklisted` / `isRefreshTokenStored`: boolean existence checks using `RedisTemplate.hasKey`.
  - `revokeRefreshToken(hash)`: deletes the `refresh:<hash>` key.
- Consumers: `OperatorService` uses these methods during login, logout, and refresh flows to enforce revocation.

### 2. Spring Cache Usage
Caching is applied through `@Cacheable` / `@CacheEvict` annotations. All cached values reside in Redis via the configured `RedisCacheManager`.

| Cache name        | Key pattern                          | Read entry point(s)                                      | Invalidation trigger(s) |
|-------------------|--------------------------------------|-----------------------------------------------------------|-------------------------|
| `operators:list`  | `"<offset>:<limit>"` string         | `OperatorService.listOperators`                           | Operator create/update/delete/password reset (`@CacheEvict allEntries=true` or keyed)|
| `operators:byId`  | Operator ID                          | `OperatorService.getOperator`                             | Same service methods that mutate operator state (`@CacheEvict` by key/all)|
| `devices:list`    | `"<offset>:<limit>"` string         | `DeviceService.listDevices`                               | Device create/update/delete (`@CacheEvict allEntries=true` or keyed)|
| `devices:byId`    | Device ID                            | `DeviceService.getDeviceById`                             | Device mutations (`@CacheEvict` by key/all)|
| `devices:byUuid`  | Device UUID string                   | `DeviceService.getDeviceByUuid`                           | Device mutations (`@CacheEvict` by key/all)|
| `apks:all`        | Constant `'all'`                     | `ApkService.getAllApks`                                   | APK create/update/delete (`@CacheEvict allEntries=true`)|
| `apks:byId`       | APK Mongo ID                         | `ApkService.findById`                                     | APK mutations (`@CacheEvict allEntries=true`)|
| `apks:query`      | `criteria.hashCode()` as string      | `ApkService.queryApks`                                    | APK mutations (`@CacheEvict allEntries=true`)|

### 3. Other Interactions
- No additional `RedisTemplate` consumers beyond `RedisTokenService` were detected.
- There are no direct Lua scripts, Pub/Sub channels, or Redis Streams in current code.

## Operations Checklist
- **Starting Redis locally**: `docker compose up redis` (relies on `.env.*` values or shell exports).
- **Manual inspection**: `redis-cli -h <host> -p <port> -a <password> keys '*'` to view keys, `ttl <key>` to verify expiration.
- **Clearing caches**: use `redis-cli flushdb` (affects every cache) or delete individual keys (`del operators:list::0:20`).
- **Adjust cache policy**: change TTL or serialization inside `RedisConfig.cacheManager(...)` or the `RedisCacheConfiguration` builder.
- **Updating credentials**: modify `.env.*` files (for containers) or override `spring.data.redis.*` via deployment environment variables.

## Testing Considerations
- Integration tests that hit Redis should activate the `test` profile; ensure `REDIS_PASSWORD` is provided when the container enforces one.
- When running Redis-less tests, consider mocking `RedisTokenService` or setting `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration` if needed.
