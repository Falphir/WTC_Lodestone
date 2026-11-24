# Hub authentication

How admins, the bot and game servers sign in to the hub, and how `SecurityConfig` keeps the two bearer schemes apart.

`GameServer` (entity, table `servers`) stores only a SHA-256 hash of each server's bearer token (`TokenService`), never the plaintext. `ServerService.register` generates a token and returns it once (via the admin API). It can't be retrieved again.

Auth is two separate schemes layered by `SecurityConfig`:
- **`/api/admin/**` and `/api/auth/**`:** admins exchange username + password (HTTP Basic/form) for a JWT at `POST /api/auth/token` (`AuthController`, 8h lifetime, roles in a `roles` claim). They then send it as `Authorization: Bearer <jwt>` for everything else under `/api/admin/**`, verified by Spring's OAuth2 resource server (`ROLE_ADMIN`).
  - **The signing key** (`SecurityConfig.jwtKey()`) comes from `lodestone.jwt-secret` (`LODESTONE_JWT_SECRET`, any passphrase, SHA-256'd to the 32 bytes HMAC-SHA256 needs). Left unset it's random per boot, so restarting the hub signs out every admin.
  - **No `WWW-Authenticate` header:** Basic auth here answers with a bare `401` (`HttpStatusEntryPoint`) on purpose. That header makes the browser pop its own credentials box, which can't sign anyone into the dashboard.
- **`/api/bridge/**`:** `Authorization: Bearer <token>` from a game server, checked by `ServerTokenFilter`. It's a servlet filter that runs before `BasicAuthenticationFilter`, looks up the token hash via `ServerService.authenticate`, and fills the `SecurityContext` with `ROLE_SERVER`; the server's `id` becomes the principal name. 401s here are a plain `401`, not a login page or redirect.
- **`/api/admin/live` (WebSocket)** is the one exception that takes the admin JWT as `?access_token=` instead of a header, since browsers can't set WebSocket headers. See `SecurityConfig.adminBearerTokens()`.

**Never mix the two:** bridge tokens and admin JWTs are both `Authorization: Bearer ...`, and must never be routed into the other's validator. `adminBearerTokens()` explicitly returns `null` for `/api/bridge/**`, so the JWT decoder never sees a server token.

**The Discord bot** is an ordinary admin account, not a separate scheme: see `docs/discord-bot.md`.

## Sign-in throttling

`httpBasic` is accepted on every `/api/admin/**` request, not just `/api/auth/token`, so the throttle sits on authentication itself, not on one URL:
- **What counts:** `service/LoginThrottle` listens to Spring's `AuthenticationFailureBadCredentialsEvent`/`AuthenticationSuccessEvent`, for username + password tokens only (Basic anywhere, the `/login` form), never JWTs.
- **The limits:** an IP locks after 5 failures, a username after 20. A lock lasts 1 minute, doubling with each failure after that, up to 15 minutes. A key with no failure for 15 minutes is forgotten. Constants, not config.
- **Per IP first:** the username lock only holds back IPs that have failed recently themselves. Someone guessing `discord-bot` from elsewhere locks that name for their own IPs, but the bot, on a clean `127.0.0.1` with the right password, still gets in.
- **A success** clears that IP's count, not the username's: one good sign-in shouldn't wipe a guessing run from somewhere else.
- **While locked:** `config/LoginThrottleFilter` (before `UsernamePasswordAuthenticationFilter` and Basic) answers `429` with `Retry-After: <seconds>` and an empty body. It answers before the password is checked, and the same way whether the username exists or not.
- **The client IP:** `server.forward-headers-strategy: native` turns on Tomcat's `RemoteIpValve`, which takes the client IP from `X-Forwarded-For`. It only does that when the request comes from loopback or a private range, which covers nginx and the Docker variant's bridge gateway. A request straight from the internet can't set its own IP that way.
- **In memory:** a hub restart forgets every count and lock.
- **Audited:** the first lock of an IP or a username records `login.lockout` (actor `system`). See `docs/audit-log.md`.

nginx's `limit_req` on `/api/auth/` and `/login` (`deploy/nginx/lodestone.conf`) stays as the first line. This throttle covers the other endpoints, and a hub without nginx in front of it.
