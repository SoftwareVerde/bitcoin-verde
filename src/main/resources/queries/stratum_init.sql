CREATE TABLE workers (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY workers_uq (username)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE worker_shares (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    worker_id INT UNSIGNED NOT NULL,
    difficulty INT UNSIGNED NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY worker_shares_worker_id_fk (worker_id) REFERENCES workers (id)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

INSERT INTO metadata (version, timestamp) VALUES (1, UNIX_TIMESTAMP());
