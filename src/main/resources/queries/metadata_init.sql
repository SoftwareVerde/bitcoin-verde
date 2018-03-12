CREATE TABLE IF NOT EXISTS metadata (
    id int unsigned NOT NULL AUTO_INCREMENT,
    version int unsigned NOT NULL DEFAULT '1',
    timestamp bigint unsigned NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY metadata_version_uq (version)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;