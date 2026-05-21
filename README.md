# notes-backend

`notes-backend` - серверная часть проекта Mutlaboc Notes. Сервис хранит данные
пользователей, заметки, чек-листы, домашние информационные карточки и сессии
Android-приложения. Backend выделен в отдельный репозиторий, чтобы мобильный
клиент оставался тонким UI-слоем, а хранение данных, аутентификация и проверка
доступа выполнялись на сервере.

Проект связан с выпускной квалификационной работой
«Разработка мобильного приложения для управления заметками на платформе
Android» и является backend-компонентом клиент-серверного прототипа
персонального органайзера.

## Возможности

- Регистрация и вход по email/password.
- JWT access tokens и refresh tokens с ротацией и logout.
- Social auth endpoints для Google и Yandex.
- CRUD для заметок, задач, чек-листов и статуса выполнения.
- CRUD для домашних информационных карточек.
- Миграции БД через Flyway.
- Health endpoints для базовой диагностики сервиса и подключения к БД.
- Единый JSON-контракт ошибок для Android-клиента.

## API-группы

| Группа | Назначение |
| --- | --- |
| `/auth` | Регистрация, login, refresh, logout, текущий пользователь и social auth. |
| `/notes` | Создание, чтение, обновление, удаление заметок и переключение completion. |
| `/home-cards` | Домашние карточки с полями, ссылками, разделами и заметками. |
| `/health` | Проверка доступности приложения и защищенная проверка БД. |

Все пользовательские ресурсы защищены Bearer token и привязаны к текущему
пользователю.

## Технологии

- Kotlin 2.3, Gradle Kotlin DSL.
- Ktor 3.4, Netty, Content Negotiation, Status Pages, Call Logging.
- PostgreSQL, HikariCP, Exposed, Flyway.
- JWT через `java-jwt`, bcrypt для password hashing.
- kotlinx.serialization для JSON.
- JUnit, Ktor test host и Testcontainers для интеграционных тестов.

## Локальный запуск

Сервис ожидает конфигурацию через переменные окружения или Gradle process
environment:

```properties
DB_JDBC_URL=jdbc:postgresql://localhost:5432/notes_backend
DB_USERNAME=notes_backend
DB_PASSWORD=change-me
JWT_SECRET=change-me-to-a-long-random-secret
GOOGLE_WEB_CLIENT_ID=optional-google-web-client-id
YANDEX_CLIENT_ID=optional-yandex-client-id
YANDEX_CLIENT_SECRET=optional-yandex-client-secret
```

Минимальная проверка:

```powershell
cd D:\Projects\notes-backend
.\gradlew.bat test
.\gradlew.bat run
```

При успешном запуске Ktor слушает `http://0.0.0.0:8080`. Для Android Emulator
dev flavor мобильного приложения обращается к этому backend через
`http://10.0.2.2:8080/`.

## Сборка

Основные Gradle-задачи:

| Команда | Назначение |
| --- | --- |
| `.\gradlew.bat test` | Запустить тесты. |
| `.\gradlew.bat build` | Собрать проект и выполнить проверки. |
| `.\gradlew.bat run` | Запустить Ktor-сервер локально. |
| `.\gradlew.bat shadowJar` | Собрать fat JAR `notes-backend-all.jar`. |

## Рабочий процесс

Основная интеграционная ветка - `develop`, как и в Android-репозитории
`Mutlaboc/MutlabocNotes`. Ветка `master` используется как стабильная ветка для
подготовленного release-состояния.

Перед публикацией или деплоем не коммитьте локальные файлы конфигурации:
`local.properties`, `.env`, IDE-файлы, логи и credentials. Production-секреты
должны храниться только во внешней среде выполнения или в GitHub Actions
secrets.

## Дополнительная документация

- [RELEASE_RUNBOOK.ru.md](RELEASE_RUNBOOK.ru.md) - rollout, миграции, smoke
  checks и rollback.
- [RELEASE_RUNBOOK.md](RELEASE_RUNBOOK.md) - английская версия runbook.

Лицензия не указана, поэтому права на использование кода не предоставляются
автоматически.
