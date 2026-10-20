-- Who claimed which airdrop, and when (epoch millis).
CREATE TABLE {prefix}claims (
    id         INTEGER PRIMARY KEY,
    airdrop    VARCHAR(64) NOT NULL,
    player     CHAR(36)    NOT NULL,
    claimed_at BIGINT      NOT NULL
);
CREATE INDEX {prefix}claims_airdrop ON {prefix}claims (airdrop);
