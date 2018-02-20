CREATE TABLE metadata (
    id int unsigned NOT NULL AUTO_INCREMENT,
    version int unsigned DEFAULT '1',
    timestamp bigint unsigned,
    PRIMARY KEY (id),
    UNIQUE KEY version_uq (version)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;

CREATE TABLE blocks (
    id int unsigned NOT NULL AUTO_INCREMENT,
    hash varchar(64) NOT NULL,
    previous_block_id int unsigned,
    merkle_root varchar(64) NOT NULL,
    version int NOT NULL DEFAULT '1',
    timestamp bigint unsigned,
    difficulty varchar(8),
    nonce bigint unsigned,
    PRIMARY KEY (id),
    UNIQUE KEY hash_uq (hash),
    FOREIGN KEY previous_block_id_ix (previous_block_id) REFERENCES blocks (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8;


INSERT INTO metadata (version, timestamp) VALUES (1, UNIX_TIMESTAMP());
