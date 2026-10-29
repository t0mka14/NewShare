# Orchestrator helpers

Bookkeeping checks the orchestrator runs after saving a report or changing `findings.md`.
Run them from `docs/testing/e2e/`.

- `check_links.py`: checks that every link in the findings index resolves to a heading or an
  `<a id>` anchor in its report. Prints the number of broken links, then each one. It should print `0`.
- `count_findings.py`: counts findings by area and severity. Rows count as open only when their
  status is exactly `open`, so add F-036 and F-131 by hand: their status reads "open (…)".
- `leak_scan.py`: scans every non-PNG file under `docs/testing/e2e`, as UTF-8 and UTF-16, for the
  clinic tokens and the installation IDs. The tokens come from the gitignored secrets file; the
  IDs come from `app/data*/**/*.json`. It never prints a secret, only the file that holds one. It
  also checks that the context file has no BOM. It should print `hits 0` and `no BOM`.
