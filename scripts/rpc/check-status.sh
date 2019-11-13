#!/bin/bash

(echo '{"method":"GET","query":"STATUS"}') | nc localhost 8334

