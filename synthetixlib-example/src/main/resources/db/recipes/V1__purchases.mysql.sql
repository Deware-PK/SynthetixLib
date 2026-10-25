-- MySQL needs AUTO_INCREMENT on the key, so it gets its own file for this version.
CREATE TABLE {prefix}purchases (
    id        INTEGER PRIMARY KEY AUTO_INCREMENT,
    player    CHAR(36)    NOT NULL,
    product   VARCHAR(64) NOT NULL,
    amount    INTEGER     NOT NULL,
    paid      BIGINT      NOT NULL,
    bought_at BIGINT      NOT NULL
);
