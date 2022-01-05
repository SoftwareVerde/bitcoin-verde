#!/bin/bash

# This script requires that the database be online but that the node is not running (i.e. ./scripts/run-database.sh).
# Use this script to disable post fast-sync UTXO indexing.

./scripts/connect-to-database.sh "INSERT INTO properties (\`key\`, value) VALUES ('utxo_import_indexing_has_completed', 1);"

