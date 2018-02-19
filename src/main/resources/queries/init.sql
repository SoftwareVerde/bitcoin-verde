CREATE TABLE metadata (
    id int(10) unsigned NOT NULL AUTO_INCREMENT,
    version int unsigned DEFAULT '1',
    timestamp bigint unsigned,
    PRIMARY KEY (id),
    UNIQUE KEY version_uq (version)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

INSERT INTO metadata (version, timestamp) VALUES (1, UNIX_TIMESTAMP());

CREATE TABLE blocks (
    id int(10) unsigned NOT NULL AUTO_INCREMENT,
    hash varchar(64) NOT NULL,
    merkle_root varchar(64) NOT NULL,
    version int NOT NULL DEFAULT '1',
    timestamp bigint unsigned,
    difficulty varchar(4) DEFAULT NULL,
    nonce bigint unsigned DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY hash_uq (hash)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;