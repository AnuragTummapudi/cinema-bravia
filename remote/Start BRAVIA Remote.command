#!/bin/zsh
set -eu
cd "${0:A:h}"
if [[ -d 'BRAVIA Remote.app' ]]; then
  exec open "$PWD/BRAVIA Remote.app"
fi
if [[ ! -x .venv/bin/python ]]; then
  print 'Set up the virtual environment first; see README.md.'
  exit 1
fi
if curl --silent --fail --max-time 1 http://127.0.0.1:8765/api/state >/dev/null; then
  open http://127.0.0.1:8765
  exit 0
fi
(sleep 1; open http://127.0.0.1:8765) &
exec .venv/bin/python server.py
