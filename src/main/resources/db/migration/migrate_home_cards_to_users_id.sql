BEGIN;

-- 1) Перенести существующие карточки, созданные в bridge-режиме,
--    с lookup-id на реальные users.id.
UPDATE home_cards hc
SET user_id = u.id
FROM home_cards_users_lookup lu
JOIN users u
  ON u.firebase_uid = lu.firebase_uid
WHERE hc.user_id = lu.id
  AND hc.user_id <> u.id;

-- 2) Проверка на случай, если остались карточки без соответствия users.
--    Этот SELECT должен вернуть 0 строк после успешной миграции.
-- SELECT hc.id, hc.user_id
-- FROM home_cards hc
-- LEFT JOIN users u ON u.id = hc.user_id
-- WHERE u.id IS NULL;

COMMIT;
