-- Mail for players who were offline. SQLite numbers INTEGER PRIMARY KEY by itself.
CREATE TABLE {prefix}mail (
    id     INTEGER PRIMARY KEY,
    player CHAR(36) NOT NULL,
    item   BLOB
);
