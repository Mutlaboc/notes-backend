-- База случайных событий фокус-таймера ("полоса событий" на клиенте).
-- Каталог глобальный (не per-user); клиент кэширует его в Room и роллит
-- взвешенно-случайное событие раз в минуту. Начисление наград — POST /events/claims,
-- идемпотентно по (user_id, operation_id) через focus_event_claims.

CREATE TABLE focus_events (
    event_key TEXT PRIMARY KEY,
    -- TEXT | CHARACTER_XP | SKILL_XP | ITEM | NEW_SKILL
    event_type TEXT NOT NULL CHECK (event_type IN ('TEXT', 'CHARACTER_XP', 'SKILL_XP', 'ITEM', 'NEW_SKILL')),
    -- Вес для взвешенного ролла на клиенте: чем больше, тем чаще выпадает.
    weight INTEGER NOT NULL CHECK (weight > 0),
    text_ru TEXT NOT NULL,
    text_en TEXT NOT NULL,
    -- Награды (заполняются по типу события, для TEXT всё пустое).
    character_xp INTEGER NOT NULL DEFAULT 0 CHECK (character_xp >= 0),
    skill_xp INTEGER NOT NULL DEFAULT 0 CHECK (skill_xp >= 0),
    -- ITEM: предмет, попадающий в инвентарь (ключи статов — как в character_stats).
    item_key TEXT,
    item_name_ru TEXT,
    item_name_en TEXT,
    item_description_ru TEXT,
    item_description_en TEXT,
    item_icon TEXT,
    item_slot TEXT,
    item_rarity TEXT,
    item_bonus_stat_key TEXT,
    item_bonus_stat_name_ru TEXT,
    item_bonus_stat_name_en TEXT,
    item_bonus_value INTEGER,
    -- NEW_SKILL: навык, добавляемый персонажу (уровень 1, прогресс 0).
    new_skill_key TEXT,
    new_skill_name_ru TEXT,
    new_skill_name_en TEXT
);

-- Идемпотентность начисления наград события (паттерн — character_xp_operations).
CREATE TABLE focus_event_claims (
    operation_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, operation_id)
);

-- ------------------------------------------------------------------
-- Сид каталога. Доли по суммарным весам: TEXT ~60%, CHARACTER_XP ~21%,
-- SKILL_XP ~10%, ITEM ~7%, NEW_SKILL ~2%.
-- ------------------------------------------------------------------

-- Просто текст (вес 6 каждый).
INSERT INTO focus_events (event_key, event_type, weight, text_ru, text_en) VALUES
('text-cozy-spot', 'TEXT', 6, 'Мутлабок нашёл уютное местечко под деревом и одобрительно кивает.', 'Mutlaboc found a cozy spot under the tree and nods approvingly.'),
('text-butterfly', 'TEXT', 6, 'Мимо пролетела бабочка. Мутлабок проводил её взглядом.', 'A butterfly fluttered by. Mutlaboc watched it go.'),
('text-grass-scent', 'TEXT', 6, 'Ветер принёс запах свежескошенной травы.', 'The wind carried the scent of freshly cut grass.'),
('text-cuckoo', 'TEXT', 6, 'Где-то вдалеке куковала кукушка. Хороший знак!', 'A cuckoo called somewhere far away. A good sign!'),
('text-humming', 'TEXT', 6, 'Мутлабок напевает песенку — работа спорится.', 'Mutlaboc hums a tune — the work is going well.'),
('text-tit-porch', 'TEXT', 6, 'На крыльцо присела синица и тут же упорхнула.', 'A tit landed on the porch and flitted off at once.'),
('text-sunbeam', 'TEXT', 6, 'Солнечный луч пробился сквозь листву.', 'A sunbeam broke through the leaves.'),
('text-hat', 'TEXT', 6, 'Мутлабок поправил шляпу и продолжил наблюдать.', 'Mutlaboc adjusted his hat and kept watching.'),
('text-dew', 'TEXT', 6, 'В траве блеснула роса. Красота!', 'Dew glistened in the grass. Lovely!'),
('text-neighbour', 'TEXT', 6, 'Сосед помахал рукой через забор.', 'A neighbour waved over the fence.'),
('text-mint-tea', 'TEXT', 6, 'Мутлабок заварил чай с мятой. Пахнет чудесно.', 'Mutlaboc brewed some mint tea. It smells wonderful.'),
('text-cat-cloud', 'TEXT', 6, 'Облако над двором похоже на кота.', 'The cloud above the yard looks like a cat.'),
('text-hedgehog', 'TEXT', 6, 'Под крыльцом кто-то тихонько шуршит. Наверное, ёжик.', 'Something rustles softly under the porch. Probably a hedgehog.'),
('text-yarn', 'TEXT', 6, 'Мутлабок распутал клубок ниток. Мелочь, а приятно.', 'Mutlaboc untangled a ball of yarn. A small win, but a nice one.'),
('text-firewood', 'TEXT', 6, 'Дрова в поленнице сложены идеально ровно.', 'The firewood is stacked perfectly straight.'),
('text-tomato', 'TEXT', 6, 'На подоконнике зреет помидор. Уже почти красный.', 'A tomato is ripening on the windowsill. Almost red now.'),
('text-old-chest', 'TEXT', 6, 'Мутлабок вытер пыль со старого сундука.', 'Mutlaboc dusted off the old chest.'),
('text-cricket', 'TEXT', 6, 'Сверчок настраивает свою скрипку к вечеру.', 'A cricket is tuning its fiddle for the evening.'),
('text-fresh-bread', 'TEXT', 6, 'Пахнет свежим хлебом. Кто-то печёт неподалёку.', 'It smells of fresh bread. Someone is baking nearby.'),
('text-yard-map', 'TEXT', 6, 'Мутлабок нашёл старую карту двора. Всё на месте.', 'Mutlaboc found an old map of the yard. Everything''s in place.'),
('text-light-rain', 'TEXT', 6, 'Лёгкий дождик прошёл стороной.', 'A light rain passed by without stopping.'),
('text-kettle', 'TEXT', 6, 'Мутлабок начистил чайник до блеска.', 'Mutlaboc polished the kettle till it shone.'),
('text-new-flower', 'TEXT', 6, 'В саду распустился новый цветок.', 'A new flower bloomed in the garden.'),
('text-silence', 'TEXT', 6, 'Тишина. Только ветер в листве и ты за делом.', 'Silence. Just the wind in the leaves and you at work.');

-- Текст + опыт персонажа (вес 5 каждый).
INSERT INTO focus_events (event_key, event_type, weight, text_ru, text_en, character_xp) VALUES
('xp-deft-hands', 'CHARACTER_XP', 5, 'Мутлабок подметил, как ловко ты справляешься.', 'Mutlaboc noticed how deftly you''re handling this.', 8),
('xp-tidiness', 'CHARACTER_XP', 5, 'Порядок вокруг вдохновляет.', 'The tidiness around is inspiring.', 10),
('xp-rhythm', 'CHARACTER_XP', 5, 'Ты вошёл в ритм — дело идёт быстрее.', 'You''ve found your rhythm — things go faster.', 12),
('xp-focus', 'CHARACTER_XP', 5, 'Отличная концентрация!', 'Excellent focus!', 15),
('xp-small-step', 'CHARACTER_XP', 5, 'Маленький шаг — тоже шаг.', 'A small step is still a step.', 6),
('xp-proud', 'CHARACTER_XP', 5, 'Мутлабок гордится тобой.', 'Mutlaboc is proud of you.', 9),
('xp-diligence', 'CHARACTER_XP', 5, 'Усердие замечено и вознаграждено.', 'Diligence noticed and rewarded.', 11),
('xp-second-wind', 'CHARACTER_XP', 5, 'Второе дыхание открылось!', 'A second wind kicks in!', 14),
('xp-master', 'CHARACTER_XP', 5, 'Настоящий мастер за работой.', 'A true master at work.', 18),
('xp-lucky-stars', 'CHARACTER_XP', 5, 'Звёзды сегодня на твоей стороне.', 'The stars are on your side today.', 25);

-- Текст + опыт навыка сессии (вес 3 каждый).
INSERT INTO focus_events (event_key, event_type, weight, text_ru, text_en, skill_xp) VALUES
('skill-muscle-memory', 'SKILL_XP', 3, 'Руки сами помнят, что делать.', 'Your hands remember what to do.', 20),
('skill-second-nature', 'SKILL_XP', 3, 'Приём отработан до автоматизма.', 'The technique is becoming second nature.', 25),
('skill-handier-way', 'SKILL_XP', 3, 'Ты придумал, как сделать это удобнее.', 'You figured out a handier way to do it.', 30),
('skill-practice', 'SKILL_XP', 3, 'Опыт приходит с практикой.', 'Experience comes with practice.', 35),
('skill-smooth-day', 'SKILL_XP', 3, 'Сегодня получается особенно ловко.', 'It''s going especially smoothly today.', 40),
('skill-trick', 'SKILL_XP', 3, 'Мутлабок подсказал полезную хитрость.', 'Mutlaboc shared a useful trick.', 45),
('skill-no-mistakes', 'SKILL_XP', 3, 'Сложный участок пройден без ошибок.', 'A tricky part done without a single mistake.', 50),
('skill-breakthrough', 'SKILL_XP', 3, 'Прорыв! Теперь всё понятно.', 'Breakthrough! Now it all makes sense.', 60);

-- Текст + предмет в инвентарь (вес 2 каждый).
INSERT INTO focus_events (
    event_key, event_type, weight, text_ru, text_en,
    item_key, item_name_ru, item_name_en, item_description_ru, item_description_en,
    item_icon, item_slot, item_rarity,
    item_bonus_stat_key, item_bonus_stat_name_ru, item_bonus_stat_name_en, item_bonus_value
) VALUES
('item-field-daisy', 'ITEM', 2, 'В траве нашлась полевая ромашка.', 'A field daisy turned up in the grass.',
 'field-daisy', 'Полевая ромашка', 'Field daisy', 'Простой цветок, а настроение поднимает.', 'A simple flower, but it lifts the spirits.',
 '🌼', NULL, 'COMMON', NULL, NULL, NULL, NULL),
('item-smooth-pebble', 'ITEM', 2, 'У дорожки лежал удивительно гладкий камешек.', 'A surprisingly smooth pebble lay by the path.',
 'smooth-pebble', 'Гладкий камешек', 'Smooth pebble', 'Приятно вертеть в руках, когда думаешь.', 'Nice to fiddle with while thinking.',
 '🪨', NULL, 'COMMON', NULL, NULL, NULL, NULL),
('item-oak-bookmark', 'ITEM', 2, 'Между досками застряла дубовая закладка.', 'An oak-leaf bookmark was stuck between the boards.',
 'oak-leaf-bookmark', 'Дубовая закладка', 'Oak-leaf bookmark', 'Хранит место в книге и запах осени.', 'Keeps your page and the scent of autumn.',
 '🍂', NULL, 'UNCOMMON', NULL, NULL, NULL, NULL),
('item-birch-whistle', 'ITEM', 2, 'На пеньке кто-то оставил берёзовый свисток.', 'Someone left a birch whistle on the stump.',
 'birch-whistle', 'Берёзовый свисток', 'Birch whistle', 'Свистит тихо, но ёжики сбегаются.', 'A quiet whistle, but hedgehogs come running.',
 '🎐', NULL, 'UNCOMMON', NULL, NULL, NULL, NULL),
('item-woolen-mittens', 'ITEM', 2, 'В сундуке нашлись шерстяные варежки.', 'Woolen mittens turned up in the chest.',
 'woolen-mittens', 'Шерстяные варежки', 'Woolen mittens', 'Связаны с любовью и очень тёплые.', 'Knitted with love and very warm.',
 '🧤', 'ACCESSORY', 'UNCOMMON', 'CONSTITUTION', 'Телосложение', 'Constitution', 1),
('item-watering-can', 'ITEM', 2, 'За сараем обнаружилась жестяная лейка.', 'A tin watering can was found behind the shed.',
 'tin-watering-can', 'Жестяная лейка', 'Tin watering can', 'Немного помята, но поливает отлично.', 'A little dented, but waters just fine.',
 '🪣', 'OFFHAND', 'RARE', 'WISDOM', 'Мудрость', 'Wisdom', 1),
('item-firefly-jar', 'ITEM', 2, 'Светлячки сами залетели в банку!', 'Fireflies flew right into the jar!',
 'firefly-jar', 'Банка со светлячками', 'Firefly jar', 'Мягкий свет для вечерних дел.', 'Soft light for evening chores.',
 '🏮', 'ACCESSORY', 'RARE', 'CHARISMA', 'Харизма', 'Charisma', 1),
('item-acorn-amulet', 'ITEM', 2, 'Под старым дубом блеснул желудёвый амулет.', 'An acorn amulet glinted under the old oak.',
 'acorn-amulet', 'Желудёвый амулет', 'Acorn amulet', 'Говорят, его носил сам прадед Мутлабока.', 'They say Mutlaboc''s great-grandfather wore it.',
 '📿', 'ACCESSORY', 'EPIC', 'WISDOM', 'Мудрость', 'Wisdom', 1);

-- Текст + новый навык (вес 1 каждый).
INSERT INTO focus_events (event_key, event_type, weight, text_ru, text_en, new_skill_key, new_skill_name_ru, new_skill_name_en) VALUES
('new-skill-gardener', 'NEW_SKILL', 1, 'Возясь с грядками, ты почувствовал призвание садовника!', 'Tending the beds, you discovered a gardener''s calling!', 'GARDENER', 'Садовник', 'Gardener'),
('new-skill-cook', 'NEW_SKILL', 1, 'Аромат из кухни пробудил в тебе повара!', 'The aroma from the kitchen awakened the cook in you!', 'COOK', 'Повар', 'Cook'),
('new-skill-fisherman', 'NEW_SKILL', 1, 'У пруда ты понял, что рождён рыбачить!', 'By the pond you realised you were born to fish!', 'FISHERMAN', 'Рыбак', 'Fisherman'),
('new-skill-beekeeper', 'NEW_SKILL', 1, 'Пчёлы приняли тебя за своего. Ты теперь пасечник!', 'The bees accepted you as one of their own. You''re a beekeeper now!', 'BEEKEEPER', 'Пасечник', 'Beekeeper'),
('new-skill-stargazer', 'NEW_SKILL', 1, 'Ночное небо открыло тебе свои секреты. Ты — звездочёт!', 'The night sky shared its secrets. You''re a stargazer now!', 'STARGAZER', 'Звездочёт', 'Stargazer');
