#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

PYTHON_SCRIPT="${DIR}/lib/reverse-endian.py"

python "${PYTHON_SCRIPT}" "$@"

