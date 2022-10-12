#!/bin/bash

q="SELECT user, time, state, substr(info, 1, 128), CAST((LENGTH(info) - LENGTH(REPLACE(info, '_binary', ''))) / LENGTH('_binary') AS INT) AS count FROM information_schema.processlist WHERE info is not null and user = 'bitcoin' ORDER BY time DESC, id;"
watch -n 0.5 "./scripts/connect-to-database.sh \"${q}\""

