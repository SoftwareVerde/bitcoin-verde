#!/bin/bash

(echo '{"method":"GET","query":"DIFFICULTY"}') | nc localhost 8334

