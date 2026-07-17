ALTER TABLE notes ADD COLUMN client_mutation_id UUID;
ALTER TABLE home_cards ADD COLUMN client_mutation_id UUID;

CREATE UNIQUE INDEX uq_notes_user_client_mutation_id
    ON notes(user_id, client_mutation_id)
    WHERE client_mutation_id IS NOT NULL;

CREATE UNIQUE INDEX uq_home_cards_user_client_mutation_id
    ON home_cards(user_id, client_mutation_id)
    WHERE client_mutation_id IS NOT NULL;

CREATE TABLE character_coin_ledger (
    operation_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stat_key TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, operation_id)
);

CREATE INDEX idx_character_coin_ledger_user_created
    ON character_coin_ledger(user_id, created_at);

CREATE TABLE character_xp_operations (
    operation_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, operation_id)
);

