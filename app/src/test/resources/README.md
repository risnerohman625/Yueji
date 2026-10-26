# Public fund-history regression sample

`007329-public-2019-2021.csv` contains published NAV and daily-return observations
for fund code 007329 through 2021-03-10, taken from the public Eastmoney
`pingzhongdata/007329.js` response while investigating the supplied 2026-10-26
screenshots. The fund client uses raw NAV ratios on ordinary days and the
published return on distribution/split dates; the fixture retains those factors.

The maximum drawdown episode peaks on 2020-04-23, bottoms on 2020-07-14, and
first recovers on 2021-03-10. Peak-to-recovery is 321 calendar days;
trough-to-recovery is 239. This sample belongs to JVM tests only and must never
be packaged as a ledger, user holding, or production Android asset.
