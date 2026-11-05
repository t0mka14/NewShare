# source from Git Bash at repo root
SCRATCH='C:\Users\koubato2\AppData\Local\Temp\5\claude\4ad6ed72b92f44cd\59d9cb92-7b9b-43cc-a66c-baa2fccbd910\scratchpad'
export PY="$SCRATCH\e2e-venv\Scripts\python.exe" E2E_STATE_DIR="$SCRATCH" PYTHONIOENCODING=utf-8
R="$(pwd -W | tr / '\')\docs\testing\e2e\scripts"
export PYTHONPATH="$R\common;$R\run-17"
export E2E_RUN_DIR="$(pwd -W | tr / '\')\docs\testing\e2e\reports\2026-11-05-run-17-questionnaire-rules"
