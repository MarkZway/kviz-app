ALTER TABLE team ADD COLUMN join_code VARCHAR(8);

UPDATE team
SET join_code = UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 6))
WHERE join_code IS NULL;

ALTER TABLE team ALTER COLUMN join_code SET NOT NULL;

CREATE UNIQUE INDEX uq_team_join_code ON team (join_code);