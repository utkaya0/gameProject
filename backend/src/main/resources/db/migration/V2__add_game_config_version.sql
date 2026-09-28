ALTER TABLE game_session
    ADD COLUMN config_version VARCHAR(32) NOT NULL DEFAULT 'color-v1';

ALTER TABLE game_session
    ALTER COLUMN config_version DROP DEFAULT;

ALTER TABLE game_session
    ADD CONSTRAINT chk_game_session_config_version CHECK (btrim(config_version) <> '');
