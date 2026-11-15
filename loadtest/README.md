# Load test

`transfers.js` is a [k6](https://k6.io) script that:

1. **setup** – creates 20 EUR accounts and deposits 1000.00 into each.
2. **load** – 50 virtual users make 5,000 transfers in total, each between two random
   accounts for a random amount from 0.01 to 200.00, each with its own `Idempotency-Key`.
   A `201` (created) or `422` (insufficient funds) is a correct response; anything else is an error.
3. **teardown** – calls `GET /admin/reconciliation` and checks that the ledger is balanced
   and that no test account has a negative balance.

The run fails (non-zero exit code) if more than 1% of requests fail, if any response is
unexpected, or if the reconciliation does not pass.

## Running it

Start the application first (from the repository root: `docker compose up --build`), then:

```bash
# With k6 installed
k6 run loadtest/transfers.js

# Without k6 installed, using Docker (run from the repository root)
docker run --rm -v "${PWD}/loadtest:/scripts" \
  -e BASE_URL=http://host.docker.internal:8080 \
  grafana/k6 run /scripts/transfers.js
```

Options, passed with `-e NAME=value`:

| Variable | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Where the API is running |
| `VUS` | `50` | Concurrent virtual users |
| `ITERATIONS` | `5000` | Total number of transfers |
| `ACCOUNTS` | `20` | Number of accounts to create and transfer between |

## Results

Measured on 2026-11-15. The complete k6 output is in
[`results/run-50vus-5000iters.txt`](results/run-50vus-5000iters.txt).

| | |
|---|---|
| Virtual users | 50 |
| Transfer requests | 5,000 |
| Transfers created (`201`) | 4,565 |
| Rejected for insufficient funds (`422`) | 435 |
| Unexpected responses / failed requests | 0 (0.00%) |
| Total run time (including setup and teardown) | 38.5 s |
| Throughput | 129.9 transfer requests/s |
| Request duration | avg 366 ms, median 360 ms, p90 453 ms, p95 487 ms, max 764 ms |
| Reconciliation after the run | `balanced: true`, ledger total 0, 0 mismatched accounts, 9,310 entries checked |
| Negative balances | none |

### Test environment

These numbers describe one developer machine, not a production deployment:

- Intel Core i5-8400 (6 cores), 32 GB RAM, Windows 11.
- Application: the Spring Boot jar run directly on the host (JDK 25), default HikariCP pool of 10 connections.
- Database: MySQL 8.4 in a Docker Desktop container (WSL 2), default configuration.
- k6 v2.4.0 run from the `grafana/k6` Docker image, reaching the application through `host.docker.internal`.

### Reading the numbers

- The result that matters is correctness under contention: 5,000 concurrent transfers across
  only 20 accounts, and the ledger still balances to exactly zero with no overdrafts.
- Latency is dominated by waiting, not work. 50 users share 10 database connections, and
  transfers that touch the same account queue behind its row lock. With 20 accounts almost
  every transfer contends with another, which is deliberate: it is the worst case for the
  locking design.
