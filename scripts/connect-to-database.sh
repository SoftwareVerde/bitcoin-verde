#!/bin/bash

USER='bitcoin'
PASSWORD='81b797117e8e0233ea8fd1d46923df54'
DATABASE='bitcoin'
PORT='8336'
HOST='127.0.0.1'

mysql -u ${USER} -h ${HOST} -P${PORT} -p${PASSWORD} ${DATABASE} 

