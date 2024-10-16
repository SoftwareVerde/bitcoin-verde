CREATE TABLE properties (
    `key` VARCHAR(255) NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE accounts (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    iterations INT UNSIGNED NOT NULL,
    payout_address VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY accounts_uq (email)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE workers (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id INT UNSIGNED NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    iterations INT UNSIGNED NOT NULL,
    was_deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY workers_uq (username),
    FOREIGN KEY workers_accounts_fk (account_id) REFERENCES accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE worker_shares (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id INT UNSIGNED NOT NULL,
    difficulty INT UNSIGNED NOT NULL,
    block_height INT UNSIGNED NOT NULL,
    hash BINARY(32) NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY worker_shares_uq (block_height, hash),
    FOREIGN KEY worker_shares_worker_id_fk (worker_id) REFERENCES workers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE found_blocks (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    worker_id INT UNSIGNED NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY found_blocks_worker_id_fk (worker_id) REFERENCES workers (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (10, UNIX_TIMESTAMP());
