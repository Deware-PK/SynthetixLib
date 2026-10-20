-- MySQL needs AUTO_INCREMENT on the key, so it gets its own file for this version.
CREATE TABLE {prefix}claims (
    id         INTEGER PRIMARY KEY AUTO_INCREMENT,
    airdrop    VARCHAR(64) NOT NULL,
    player     CHAR(36)    NOT NULL,
    claimed_at BIGINT      NOT NULL
);
CREATE INDEX {prefix}claims_airdrop ON {prefix}claims (airdrop);
