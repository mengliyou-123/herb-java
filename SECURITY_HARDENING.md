# Security hardening and rollback

The backend security changes are now in the original project directory. The
four pre-existing local edits were saved in the Git-ignored, Windows
user-encrypted `.security-backup.local` before their security-sensitive lines
were merged. The frontend changes are on its `codex/security-hardening` branch.

## Before running

1. The local `herb01` schema was checked on 2026-09-30: the password column
   is VARCHAR(255), the three collection uniqueness indexes exist, and there
   are no duplicate collection rows. For another database, apply
   [the migration](docs/security-migration.sql) after checking duplicates.
   Existing MD5 passwords are upgraded when users next log in.
2. For this local checkout, use the ignored `.env.local` with `start-local.ps1`.
   Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `HERB_JWT_SECRET`
   (at least 32 characters), `OSS_ACCESS_KEY_ID`, `OSS_ACCESS_KEY_SECRET`,
   and `ZHIPU_API_KEY` in the backend process environment.
   Optional: `REDIS_HOST`, `REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD` when Redis requires authentication, and comma-separated
   `HERB_ALLOWED_ORIGINS`. Use a dedicated MySQL user with only the required
   database privileges. Use TLS for database and Redis connections in production.
   This local checkout now uses `herb_local_app` with only SELECT, INSERT,
   UPDATE, and DELETE on `herb01`; its password is in the ignored `.env.local`.
   `start-local.ps1` uses the project HTTPS Maven settings.
3. Revoke and rotate the old database, OSS, AI, and JWT credentials. Removing
   plaintext from the current tree does not remove it from older commits.
4. Configure the OSS bucket to serve uploads from a separate domain with safe
   content types. The private upload endpoint sets object ACL to private and
   returns short-lived signed access. Verify bucket policy and ACL behavior in
   the deployed OSS account; use a separate private bucket for medical images.
5. Run the backend and frontend behind HTTPS. Configure HSTS and a suitable
   Content Security Policy at the web server. Never expose the Vite dev server
   as a production server.
6. On this Windows host, run `secure-local-ports.ps1` in an administrator
   PowerShell to block remote inbound MySQL and Redis access. The current
   process cannot install firewall rules without Windows administrator rights.
   The script adds named rules; remove the two `Herb Block Remote ...` rules
   to reverse them. Keep both services accessible only from loopback.

## What changed

- Registration uses a username, password, and contact email again. Login uses
  the username. Email verification and SMTP setup were removed. The existing
  unique email index may remain in the local database; it does not require
  email verification or change the login identifier.

- Authenticated requests look up the current user and enforce admin permission
  for management routes. User profiles, posts, comments, collections, and AI
  history enforce ownership on writes.
- Passwords use salted PBKDF2. Login and registration are rate limited. Logout,
  password changes, and user deletion revoke Redis sessions.
- Uploads accept decoded JPEG/PNG images of bounded dimensions, re-encode them,
  and enforce a daily quota. AI requests and pagination are bounded.
- Internal exceptions are hidden from API responses. Allowed CORS origins are
  configured explicitly.
- The frontend sanitizes rich HTML, keeps tokens in memory, and has
  updated vulnerable npm dependencies.
- The backend binds to loopback by default; set `HERB_BIND_ADDRESS` only when a
  trusted reverse proxy requires another interface. API docs are disabled by
  default; set `HERB_API_DOCS_ENABLED=true` only in a trusted development setup.
- The AI SDK's transitive Fastjson dependency is overridden to 1.2.84.
- The bundled AI SDK's `ChatApiService` is replaced by a source-compatible
  class that uses `PropertyNamingStrategies.SNAKE_CASE`; this prevents its
  initialization failure with the application's Jackson version. Recheck this
  replacement when upgrading or removing the old SDK.
- The frontend keeps authentication state in memory. Refreshing the page requires
  a new login; older browser-stored tokens are cleared on load.

## Rollback

The backend's original Git state is `c4484e8`; the four local edits are also
recoverable from `.security-backup.local` for this Windows user. Revert the
new security integration commit if code rollback is required. The frontend
branch has its own security commits and `codex/security-before` tag. Never
restore old cloud credentials as part of a rollback; rotated secrets stay
revoked.

Database changes should be reviewed separately before reversing. Widening
the password column is safe to retain. Dropping unique collection keys
would reintroduce duplicate rows.
