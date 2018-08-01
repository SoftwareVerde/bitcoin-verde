#!/bin/bash

USER='root'
PASSWORD='d3d4a3d0533e3e83bc16db93414afd96'
DATABASE='bitcoin'
PORT='8336'
HOST='127.0.0.1'

mysql -u ${USER} -h ${HOST} -P${PORT} -p${PASSWORD} ${DATABASE} 

