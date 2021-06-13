CREATE TABLE staged_utxo_commitment LIKE committed_unspent_transaction_outputs;

CREATE TABLE utxo_commitments (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    block_id INT UNSIGNED NOT NULL,
    hash BINARY(32),
    PRIMARY KEY (id),
    FOREIGN KEY utxo_commitment_block_id_fk (block_id) REFERENCES blocks (id)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE utxo_commitment_buckets (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    utxo_commitment_id INT UNSIGNED NOT NULL,
    `index` INT UNSIGNED NOT NULL,
    public_key BINARY(33) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY utxo_commitment_buckets_uq (utxo_commitment_id, `index`),
    FOREIGN KEY utxo_commitment_bucket_fk (utxo_commitment_id) REFERENCES utxo_commitments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;

CREATE TABLE utxo_commitment_files (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    utxo_commitment_bucket_id INT UNSIGNED NOT NULL,
    sub_bucket_index INT UNSIGNED NOT NULL,
    public_key BINARY(33) NOT NULL,
    utxo_count INT NOT NULL,
    byte_count INT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY utxo_commitment_file_fk (utxo_commitment_bucket_id) REFERENCES utxo_commitment_buckets (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;