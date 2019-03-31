#!/bin/bash

USER='root'
PASSWORD='d3d4a3d0533e3e83bc16db93414afd96'
DATABASE='bitcoin'
PORT='8336'
HOST='127.0.0.1'

# Adapted from: https://gist.githubusercontent.com/esperlu/943776/raw/be469f0a0ab8962350f3c5ebe8459218b915f817/mysql2sqlite.sh

mysqldump  --no-data --column-statistics=0 --compatible=ansi --skip-extended-insert --compact -u ${USER} -h ${HOST} -P${PORT} -p${PASSWORD} ${DATABASE} | \

awk '

BEGIN {
	FS=",$"
}

# CREATE TRIGGER statements have funny commenting.  Remember we are in trigger.
/^\/\*.*CREATE.*TRIGGER/ {
	gsub( /^.*TRIGGER/, "CREATE TRIGGER" )
	print
	inTrigger = 1
	next
}

# The end of CREATE TRIGGER has a stray comment terminator
/END \*\/;;/ { gsub( /\*\//, "" ); print; inTrigger = 0; next }

# The rest of triggers just get passed through
inTrigger != 0 { print; next }

# Skip other comments
/^\/\*/ { next }

# Print all `INSERT` lines. The single quotes are protected by another single quote.
/INSERT/ {
	gsub( /\\\047/, "\047\047" )
	gsub(/\\n/, "\n")
	gsub(/\\r/, "\r")
	gsub(/\\"/, "\"")
	gsub(/\\\\/, "\\")
	gsub(/\\\032/, "\032")
	print
	next
}

# Print the `CREATE` line as is and capture the table name.
/^CREATE/ {
	print
	if ( match( $0, /\"[^\"]+/ ) ) tableName = substr( $0, RSTART+1, RLENGTH-1 ) 
}

# Replace `FULLTEXT KEY` or any other `XXXXX KEY` except PRIMARY by `KEY`
/^  [^"]+KEY/ && !/^  PRIMARY KEY/ { gsub( /.+KEY/, "  KEY" ) }

# Get rid of field lengths in KEY lines
/ KEY/ { gsub(/\([0-9]+\)/, "") }

# Print all fields definition lines except the `KEY` lines.
/^  / && !/^(  KEY|\);)/ {
	gsub( /AUTO_INCREMENT|auto_increment/, "" )
	gsub( /(CHARACTER SET|character set) [^ ]+ /, "" )
	gsub( /DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP|default current_timestamp on update current_timestamp/, "" )
	gsub( /(COLLATE|collate) [^ ]+ /, "" )
	gsub(/(ENUM|enum)[^)]+\)/, "text ")
	gsub(/(SET|set)\([^)]+\)/, "text ")
	gsub(/UNSIGNED|unsigned/, "")
	if (prev) print prev ","
	prev = $1
}

# `KEY` lines are extracted from the `CREATE` block and stored in array for later print 
# in a separate `CREATE KEY` command. The index name is prefixed by the table name to 
# avoid a sqlite error for duplicate index name.
/^(  KEY|\);)/ {
	if (prev) print prev
	prev=""
	if ($0 == ");"){
		print
	} else {
		if ( match( $0, /\"[^"]+/ ) ) indexName = substr( $0, RSTART+1, RLENGTH-1 ) 
		if ( match( $0, /\([^()]+/ ) ) indexKey = substr( $0, RSTART+1, RLENGTH-1 ) 
		key[tableName]=key[tableName] "CREATE INDEX \"" tableName "_" indexName "\" ON \"" tableName "\" (" indexKey ");\n"
	}
}

# Print all `KEY` creation lines.
END {
	for (table in key) printf key[table]
}
' | sed 's/int(10)/INTEGER/g'
exit 0

