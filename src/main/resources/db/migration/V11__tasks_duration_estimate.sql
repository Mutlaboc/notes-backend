-- Обычные задачи (TASKS) получили необязательную «предполагаемую продолжительность».
-- Ограничение из V7 требовало duration_minutes IS NULL для всех категорий, кроме
-- повторяющихся, из-за чего вставка обычной задачи с оценкой падала с 500.
-- Теперь: для TASKS duration_minutes либо NULL, либо > 0; для SHOPPING — по-прежнему NULL.
ALTER TABLE notes DROP CONSTRAINT notes_recurring_fields_check;
ALTER TABLE notes ADD CONSTRAINT notes_recurring_fields_check CHECK (
    (category = 'RECURRING_TASKS' AND start_at IS NOT NULL AND duration_minutes > 0 AND repeat_rule <> 'NONE')
    OR
    (category = 'TASKS' AND start_at IS NULL AND (duration_minutes IS NULL OR duration_minutes > 0) AND repeat_rule = 'NONE')
    OR
    (category = 'SHOPPING' AND start_at IS NULL AND duration_minutes IS NULL AND repeat_rule = 'NONE')
);
