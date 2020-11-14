#!/bin/bash
ps aux | grep -e NODE -e mysql -e EXPLORER | grep -v 'grep' | awk '{print $2 " " $11 " " $(NF-1)}' | cut -c 1-36
