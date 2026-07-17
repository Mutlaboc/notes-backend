ALTER TABLE notes ADD COLUMN start_at TIMESTAMPTZ;
ALTER TABLE notes ADD COLUMN duration_minutes BIGINT;
ALTER TABLE notes ADD COLUMN recurrence_parent_id UUID REFERENCES notes(id) ON DELETE SET NULL;
ALTER TABLE notes ADD COLUMN recurrence_anchor_day INTEGER;

-- The legacy constraint only permits SHOPPING/TASKS/NOTES. Drop it before
-- converting repeating TASKS to RECURRING_TASKS; the final stricter constraint
-- is installed below in the same transaction.
ALTER TABLE notes DROP CONSTRAINT notes_category_check;

UPDATE notes
SET category = 'RECURRING_TASKS',
    start_at = COALESCE(deadline_at, created_at),
    duration_minutes = 60,
    recurrence_anchor_day = EXTRACT(DAY FROM COALESCE(deadline_at, created_at))::INTEGER,
    deadline_at = NULL
WHERE category = 'TASKS' AND repeat_rule <> 'NONE';

UPDATE notes SET category = 'TASKS' WHERE category = 'NOTES';

ALTER TABLE notes ADD CONSTRAINT notes_category_check
    CHECK (category IN ('SHOPPING', 'TASKS', 'RECURRING_TASKS'));
ALTER TABLE notes ADD CONSTRAINT notes_recurring_fields_check CHECK (
    (category = 'RECURRING_TASKS' AND start_at IS NOT NULL AND duration_minutes > 0 AND repeat_rule <> 'NONE')
    OR
    (category <> 'RECURRING_TASKS' AND start_at IS NULL AND duration_minutes IS NULL AND repeat_rule = 'NONE')
);
ALTER TABLE notes ADD CONSTRAINT notes_recurrence_anchor_day_check
    CHECK (recurrence_anchor_day IS NULL OR recurrence_anchor_day BETWEEN 1 AND 31);

CREATE UNIQUE INDEX uq_notes_recurrence_parent_id
    ON notes(recurrence_parent_id)
    WHERE recurrence_parent_id IS NOT NULL;

CREATE INDEX idx_notes_user_start_at
    ON notes(user_id, start_at)
    WHERE start_at IS NOT NULL;
