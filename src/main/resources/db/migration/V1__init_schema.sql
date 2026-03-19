CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email CITEXT UNIQUE,
    password_hash TEXT,
    firebase_uid TEXT UNIQUE,
    display_name TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ
);

CREATE TABLE notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_firestore_id TEXT,
    title TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'NOTES',
    deadline_at TIMESTAMPTZ,
    is_repeating BOOLEAN NOT NULL DEFAULT FALSE,
    coin_count INTEGER NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT notes_category_check
        CHECK (category IN ('SHOPPING', 'TASKS', 'NOTES')),

    CONSTRAINT notes_coin_count_check
        CHECK (coin_count >= 0)
);

CREATE TABLE note_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    text TEXT NOT NULL DEFAULT '',
    is_checked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT note_checklist_items_position_check
        CHECK (position >= 0),

    CONSTRAINT note_checklist_items_note_position_unique
        UNIQUE (note_id, position)
);

CREATE TABLE home_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_firestore_id TEXT,
    title TEXT NOT NULL DEFAULT '',
    section TEXT NOT NULL DEFAULT 'OTHER',
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT home_cards_section_check
        CHECK (section IN ('METERS', 'APPLIANCES', 'LIGHTING', 'DOCUMENTS', 'CONTACTS', 'OTHER'))
);

CREATE TABLE home_card_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES home_cards(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    field_key TEXT NOT NULL DEFAULT '',
    field_value TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT home_card_fields_position_check
        CHECK (position >= 0),

    CONSTRAINT home_card_fields_card_position_unique
        UNIQUE (card_id, position)
);

CREATE TABLE home_card_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES home_cards(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT home_card_links_position_check
        CHECK (position >= 0),

    CONSTRAINT home_card_links_card_position_unique
        UNIQUE (card_id, position),

    CONSTRAINT home_card_links_url_not_blank
        CHECK (BTRIM(url) <> '')
);

CREATE UNIQUE INDEX uq_notes_user_source_firestore_id
    ON notes(user_id, source_firestore_id)
    WHERE source_firestore_id IS NOT NULL;

CREATE UNIQUE INDEX uq_home_cards_user_source_firestore_id
    ON home_cards(user_id, source_firestore_id)
    WHERE source_firestore_id IS NOT NULL;

CREATE INDEX idx_notes_user_updated_at
    ON notes(user_id, updated_at DESC);

CREATE INDEX idx_notes_user_completed
    ON notes(user_id, is_completed);

CREATE INDEX idx_notes_user_category
    ON notes(user_id, category);

CREATE INDEX idx_notes_user_deadline_at
    ON notes(user_id, deadline_at)
    WHERE deadline_at IS NOT NULL;

CREATE INDEX idx_note_checklist_items_note_position
    ON note_checklist_items(note_id, position);

CREATE INDEX idx_home_cards_user_updated_at
    ON home_cards(user_id, updated_at DESC);

CREATE INDEX idx_home_cards_user_section
    ON home_cards(user_id, section);

CREATE INDEX idx_home_card_fields_card_position
    ON home_card_fields(card_id, position);

CREATE INDEX idx_home_card_links_card_position
    ON home_card_links(card_id, position);

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_notes_set_updated_at
BEFORE UPDATE ON notes
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_home_cards_set_updated_at
BEFORE UPDATE ON home_cards
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
