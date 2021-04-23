CREATE TABLE double_spend_proofs (
    hash BINARY(32) NOT NULL,
    transaction_id INT UNSIGNED NOT NULL,
    output_index INT UNSIGNED NOT NULL,
    data BLOB,
    PRIMARY KEY (hash),
    UNIQUE KEY dsproof_previous_output_ix (transaction_id, output_index)
) ENGINE=InnoDB DEFAULT CHARSET=LATIN1;
