# Backend release runbook

Use this runbook with the Android release checklist in `MutlabocNotes`. It documents the
manual rollout, migration, staging smoke, and rollback expectations for `notes-backend`.
It does not introduce new endpoints, schemas, CI jobs, or deploy automation.

## Pre-rollout checks

Run from the backend repository root:

```powershell
.\gradlew.bat test
```

Before touching staging or production, record:

- Release owner.
- Backend commit SHA and artifact or container tag.
- Target environment.
- Database backup reference.
- Rollback owner and previous artifact or container tag.
- Android RC build that will be used for compatibility smoke.

## Migration notes

The backend uses Flyway migrations from `src/main/resources/db/migration`:

| Migration | Purpose | Rollout note |
| --- | --- | --- |
| `V1__init_schema.sql` | Initial users, notes, checklist, triggers, and indexes | Requires PostgreSQL extensions used by the schema; verify backup before first rollout. |
| `V2__add_local_auth_to_users.sql` | Local auth fields on users | Verify existing social/Firebase users remain readable. |
| `V3__create_refresh_tokens.sql` | Refresh token persistence | Verify login, refresh, logout, and revoked-token behavior. |
| `V4__create_user_identities.sql` | Social identity linking | Verify Google/Yandex identity flows or not-ready errors are expected for the environment. |

Migration rules:

- Take and verify a database backup before rollout.
- Let Flyway apply migrations once through the normal application startup/deploy path.
- Do not manually edit Flyway metadata or production tables during rollout.
- If migration fails, stop rollout immediately and preserve logs, artifact tag, and backup reference.
- Treat down-migration as environment-specific; do not assume every schema change is reversible.

## Staging smoke checklist

Run this against staging after deployment and before Android production rollout.

| Area | Check | Expected result | Status |
| --- | --- | --- | --- |
| Public health | `GET /health` | `200 OK` with service health payload | TBD |
| Authenticated DB health | `GET /health/db` with a valid bearer token | `200 OK`; no sensitive database metadata in response | TBD |
| Register | `POST /auth/register` with a staging test account | `200 OK` with access and refresh tokens | TBD |
| Login | `POST /auth/login` | `200 OK` with access and refresh tokens | TBD |
| Current user | `GET /auth/me` | `200 OK` for valid token; `401` for missing/invalid token | TBD |
| Refresh | `POST /auth/refresh` | New session issued; old rotated refresh token rejected when applicable | TBD |
| Logout | `POST /auth/logout` | Logout succeeds; revoked refresh token cannot restore session | TBD |
| Notes CRUD | `GET/POST/PUT/DELETE /notes` | User-scoped notes work and invalid IDs return unified errors | TBD |
| Completion | `PATCH /notes/{id}/completion` | Owner can toggle completion; anonymous or other user cannot | TBD |
| Home cards CRUD | `GET/POST/PUT/DELETE /home-cards` | User-scoped home cards work and validation errors are stable | TBD |
| Android compatibility | Android RC smoke against staging | Auth, notes, home-cards, settings, and logout pass | TBD |

## Backward compatibility checks

Before production rollout, verify:

- Previous Android build works with the new backend for auth, `/auth/me`, notes,
  completion, home-cards, refresh, and logout.
- New Android build works with the current backend for the same critical paths.
- New Android build works with the new backend for the full RC matrix.
- Error responses remain compatible with the Android error mapper.

## Rollback checklist

Use rollback when rollout causes migration failure, auth/session failure, data integrity
risk, or repeated Android compatibility failures.

| Step | Owner | Evidence | Status |
| --- | --- | --- | --- |
| Stop or pause production rollout | TBD | Deploy system reference | TBD |
| Preserve failing artifact/container tag and logs | TBD | Log bundle / tag | TBD |
| Confirm database backup reference is available | TBD | Backup ID / timestamp | TBD |
| Revert backend artifact or container to previous known-good version | TBD | Artifact / tag | TBD |
| Validate `GET /health` | TBD | Response / timestamp | TBD |
| Validate authenticated `GET /health/db` | TBD | Response / timestamp | TBD |
| Run Android smoke against restored backend | TBD | Device/build notes | TBD |
| Document whether any Flyway migration is irreversible | TBD | Decision record | TBD |
| Communicate final status and follow-up issue links | TBD | Issue / release note | TBD |

Rollback caveats:

- Prefer restoring the previous backend artifact first when the schema remains compatible.
- Use database restore only with explicit release-owner approval and a known backup point.
- If a migration has already changed production data, document the exact recovery decision
  before modifying the database.
