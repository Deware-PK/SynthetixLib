-- Lots on the auction house.
CREATE TABLE {prefix}lots (
    id    CHAR(36)     PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    price BIGINT       NOT NULL
);
