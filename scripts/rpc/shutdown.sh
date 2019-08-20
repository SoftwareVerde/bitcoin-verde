#!/bin/bash

(echo '{"method":"POST","query":"SHUTDOWN"}') | nc localhost 8334

