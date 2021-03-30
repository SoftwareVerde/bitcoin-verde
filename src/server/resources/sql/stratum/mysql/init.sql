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
    PRIMARY KEY (id),
    UNIQUE KEY workers_uq (username),
    FOREIGN KEY workers_accounts_fk (account_id) REFERENCES accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE worker_shares (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id INT UNSIGNED NOT NULL,
    difficulty INT UNSIGNED NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY worker_shares_worker_id_fk (worker_id) REFERENCES workers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (5, UNIX_TIMESTAMP());
