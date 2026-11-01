param([switch]$Generate)
# Compile the current complete source graph, including pure parsers that live only in tests.
& (Join-Path $PSScriptRoot 'test-ledger.ps1') -Generate:$Generate
exit $LASTEXITCODE
