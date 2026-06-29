-- Character sheet: one row per user (level / experience / display name).
CREATE TABLE character_sheets (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL DEFAULT '',
    level INTEGER NOT NULL DEFAULT 1,
    xp INTEGER NOT NULL DEFAULT 0,
    xp_to_next INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT character_sheets_level_check CHECK (level >= 1),
    CONSTRAINT character_sheets_xp_check CHECK (xp >= 0),
    CONSTRAINT character_sheets_xp_to_next_check CHECK (xp_to_next > 0)
);

-- D&D-style ability scores (Strength, Dexterity, …).
CREATE TABLE character_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    stat_key TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    value INTEGER NOT NULL DEFAULT 10,

    CONSTRAINT character_stats_position_check CHECK (position >= 0),
    CONSTRAINT character_stats_user_key_unique UNIQUE (user_id, stat_key)
);

CREATE INDEX idx_character_stats_user_id ON character_stats(user_id);

-- Skills (Lumberjack, Carpenter, Archivist, …) with a 0..1 progress to next level.
CREATE TABLE character_skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    skill_key TEXT NOT NULL,
    name TEXT NOT NULL,
    level INTEGER NOT NULL DEFAULT 1,
    progress DOUBLE PRECISION NOT NULL DEFAULT 0,

    CONSTRAINT character_skills_position_check CHECK (position >= 0),
    CONSTRAINT character_skills_level_check CHECK (level >= 1),
    CONSTRAINT character_skills_progress_check CHECK (progress >= 0 AND progress <= 1),
    CONSTRAINT character_skills_user_key_unique UNIQUE (user_id, skill_key)
);

CREATE INDEX idx_character_skills_user_id ON character_skills(user_id);
