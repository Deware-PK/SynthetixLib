-- MySQL needs AUTO_INCREMENT, so it gets its own file for this version.
CREATE TABLE {prefix}mail (
    id     INTEGER PRIMARY KEY AUTO_INCREMENT,
    player CHAR(36) NOT NULL,
    item   BLOB
);
