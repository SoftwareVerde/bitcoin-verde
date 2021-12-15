#!/bin/bash

USER='root'
PASSWORD='d3d4a3d0533e3e83bc16db93414afd96'
DATABASE='bitcoin'
PORT='8336'
HOST='127.0.0.1'

QUERY="$1"

if which mysql >/dev/null; then
    mysql='mysql'
else
    # Linux
    LD_LIBRARY_PATH="$(pwd)/out/mysql/lib"
    export LD_LIBRARY_PATH

    # MacOS
    DYLD_LIBRARY_PATH="${LD_LIBRARY_PATH}"
    export DYLD_LIBRARY_PATH

    mysql='./out/mysql/base/bin/mysql'
fi

if [ -z "${QUERY}" ]; then
    $mysql --binary-as-hex -u ${USER} -h ${HOST} -P${PORT} -p${PASSWORD} ${DATABASE}
else
    $mysql --binary-as-hex -u ${USER} -h ${HOST} -P${PORT} -p${PASSWORD} ${DATABASE} -e "${QUERY}"
fi

