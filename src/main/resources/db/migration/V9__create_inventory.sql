-- Инвентарь и экипировка персонажа (docs/INVENTORY_API_CONTRACT.md в Android-репозитории).

CREATE TABLE inventory_items (
    id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Стабильный ключ предмета, который клиент видит как "id" ("iron-helmet").
    item_key TEXT NOT NULL,
    position INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    icon TEXT NOT NULL DEFAULT '',
    -- HEAD | BODY | LEGS | WEAPON | OFFHAND | ACCESSORY, NULL — предмет нельзя надеть.
    slot TEXT,
    rarity TEXT NOT NULL DEFAULT 'COMMON',
    -- Слот, в который предмет надет сейчас; для надетого всегда равен slot.
    equipped_slot TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    UNIQUE (user_id, item_key)
);

-- В одном слоте может быть надет только один предмет.
CREATE UNIQUE INDEX uq_inventory_items_user_equipped_slot
    ON inventory_items(user_id, equipped_slot)
    WHERE equipped_slot IS NOT NULL;

CREATE INDEX idx_inventory_items_user_position
    ON inventory_items(user_id, position);

CREATE TABLE inventory_item_bonuses (
    item_id UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    stat_key TEXT NOT NULL,
    stat_name TEXT NOT NULL,
    value INTEGER NOT NULL,
    PRIMARY KEY (item_id, position)
);

-- Идемпотентность equip/unequip по operationId (как character_xp_operations).
CREATE TABLE inventory_operations (
    operation_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, operation_id)
);
