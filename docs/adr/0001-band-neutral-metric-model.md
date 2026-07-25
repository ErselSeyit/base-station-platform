# ADR 0001 — Band-neutral metric model

## Status
Accepted.

## Context
5G NR metrics (throughput, RSRP, SINR) are measured per frequency band (n28 /
700 MHz, n78 / 3.5 GHz). Earlier the band was baked into the metric type name
(`RSRP_NR700`), which multiplied the type enum, coupled the type to the band,
and diverged from the 3GPP model.

## Decision
A metric **type** is band-neutral (`RSRP`, `DL_THROUGHPUT`, …) and the NR band
is a separate **dimension** on the reading (`band: N28 | N78 | NONE`), matching
3GPP (a measurement is reported against a measured object — an NRCellDU — that
carries the frequency). The wire protocol (C/Go/Python) encodes the band as a
byte alongside each metric; `GET /api/v1/metrics/catalog` lists every type with
its unit and 3GPP TS 28.552 counter.

## Consequences
- One type enum instead of a combinatorial explosion; band-less metrics (CPU,
  temperature) use `NONE`.
- A migration renamed stored `*_NR700`/`*_NR3500` documents to `(type, band)`.
- Batch ingest must carry the band through (a regression that dropped it is now
  covered by a test).
