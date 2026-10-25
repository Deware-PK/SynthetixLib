-- Who bought what in the shop recipe (epoch millis).
-- @snippet:recipe-migration
CREATE TABLE {prefix}purchases (
    id        INTEGER PRIMARY KEY,
    player    CHAR(36)    NOT NULL,
    product   VARCHAR(64) NOT NULL,
    amount    INTEGER     NOT NULL,
    paid      BIGINT      NOT NULL,
    bought_at BIGINT      NOT NULL
);
-- @end
