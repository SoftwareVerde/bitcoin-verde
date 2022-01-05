CREATE TABLE stratum.properties (
    `key` VARCHAR(255) NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

CREATE TABLE stratum.found_blocks (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    hash BINARY(32) NOT NULL,
    worker_id INT UNSIGNED NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY found_blocks_worker_id_fk (worker_id) REFERENCES workers (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;

DELETE FROM stratum.worker_shares;
ALTER TABLE stratum.worker_shares ADD COLUMN block_height INT UNSIGNED NOT NULL AFTER difficulty;
ALTER TABLE stratum.worker_shares ADD COLUMN hash BINARY(32) NOT NULL AFTER block_height;
ALTER TABLE stratum.worker_shares ADD CONSTRAINT worker_shares_uq UNIQUE (block_height, hash);
ALTER TABLE stratum.workers ADD COLUMN was_deleted TINYINT(1) NOT NULL DEFAULT 0;