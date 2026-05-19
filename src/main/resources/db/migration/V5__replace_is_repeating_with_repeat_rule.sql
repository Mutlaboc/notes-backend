ALTER TABLE notes
    ADD COLUMN repeat_rule TEXT NOT NULL DEFAULT 'NONE';

UPDATE notes
SET repeat_rule = CASE
    WHEN is_repeating THEN 'DAILY'
    ELSE 'NONE'
END;

ALTER TABLE notes
    ADD CONSTRAINT notes_repeat_rule_check
        CHECK (repeat_rule IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY'));

ALTER TABLE notes
    DROP COLUMN is_repeating;
