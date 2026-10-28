# source this from Git Bash at the repo root (adjust SCRATCH): sets up PYTHONPATH etc. for the run-07 scripts
SCRATCH=${SCRATCH:-'C:\Users\koubato2\AppData\Local\Temp\5\claude\C--Users-koubato2-IdeaProjects-NewShare\30b81352-6603-49c2-b8a5-8bc8d7e343d6\scratchpad'}
export PY="$SCRATCH\e2e-venv\Scripts\python.exe" E2E_STATE_DIR="$SCRATCH\state" PYTHONIOENCODING=utf-8
R="$(pwd -W | tr / '\')\docs\testing\e2e\scripts"
export PYTHONPATH="$R\common;$R\run-07"
