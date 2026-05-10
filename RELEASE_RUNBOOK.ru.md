# Backend release runbook

Используйте этот runbook вместе с Android release checklist в `MutlabocNotes`. Он
документирует ожидания по manual rollout, migration, staging smoke и rollback для
`notes-backend`. Он не вводит новые endpoints, schemas, CI jobs или deploy automation.

## Pre-rollout checks

Запускать из корня backend-репозитория:

```powershell
.\gradlew.bat test
```

Перед изменениями в staging или production запишите:

- Release owner.
- Backend commit SHA и artifact или container tag.
- Target environment.
- Database backup reference.
- Rollback owner и previous artifact или container tag.
- Android RC build, который будет использован для compatibility smoke.

## Migration notes

Backend использует Flyway migrations из `src/main/resources/db/migration`:

| Migration | Purpose | Rollout note |
| --- | --- | --- |
| `V1__init_schema.sql` | Initial users, notes, checklist, triggers, and indexes | Требуются PostgreSQL extensions, используемые schema; проверить backup перед первым rollout. |
| `V2__add_local_auth_to_users.sql` | Local auth fields on users | Проверить, что existing social/Firebase users остаются читаемыми. |
| `V3__create_refresh_tokens.sql` | Refresh token persistence | Проверить login, refresh, logout и revoked-token behavior. |
| `V4__create_user_identities.sql` | Social identity linking | Проверить Google/Yandex identity flows или ожидаемые not-ready errors для environment. |

Migration rules:

- Сделайте и проверьте database backup перед rollout.
- Дайте Flyway применить migrations один раз через обычный application startup/deploy path.
- Не редактировать вручную Flyway metadata или production tables во время rollout.
- Если migration fails, немедленно остановить rollout и сохранить logs, artifact tag и backup reference.
- Считать down-migration environment-specific; не предполагать, что каждое schema change обратимо.

## Staging smoke checklist

Запустите против staging после deployment и перед Android production rollout.

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

Перед production rollout проверьте:

- Previous Android build работает с new backend для auth, `/auth/me`, notes,
  completion, home-cards, refresh и logout.
- New Android build работает с current backend для тех же critical paths.
- New Android build работает с new backend для полной RC matrix.
- Error responses остаются совместимыми с Android error mapper.

## Rollback checklist

Используйте rollback, если rollout вызывает migration failure, auth/session failure,
data integrity risk или повторяющиеся Android compatibility failures.

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

- Сначала предпочитайте восстановление previous backend artifact, если schema остается compatible.
- Используйте database restore только с явным approval от release owner и известной backup point.
- Если migration уже изменила production data, задокументировать точное recovery decision
  до изменения database.
