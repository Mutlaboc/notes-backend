-- Достижения: метрики-счётчики и взятые ступени (бронза/серебро/золото/платина).
-- Клиент считает прогресс офлайн и синхронизирует через outbox:
--   PUT /achievements/metrics  — снапшот метрик (merge по max: счётчики монотонные),
--   POST /achievements/unlocks — взятая ступень (идемпотентно по operation_id),
--   GET /achievements          — состояние для pull на другие устройства.

CREATE TABLE achievement_metrics (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    metric_key TEXT NOT NULL,
    value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, metric_key)
);

CREATE TABLE achievement_unlocks (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id TEXT NOT NULL,
    tier TEXT NOT NULL CHECK (tier IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM')),
    points INTEGER NOT NULL CHECK (points >= 0),
    -- Момент взятия по часам клиента (epoch millis) — для порядка тостов на других устройствах.
    unlocked_at BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, achievement_id, tier)
);

-- Идемпотентность операций достижений (паттерн — character_xp_operations).
CREATE TABLE achievement_operations (
    operation_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, operation_id)
);
