#!/bin/bash

((echo '{"method":"GET","query":"STATUS"}') && sleep 1) | nc localhost 8334

