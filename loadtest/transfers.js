// k6 load test: many virtual users make random transfers between seeded accounts at the
// same time, then the ledger is reconciled. The test fails if money was created or lost.
//
// Run (see loadtest/README.md for details):
//   k6 run loadtest/transfers.js
//   k6 run -e VUS=100 -e ITERATIONS=10000 -e BASE_URL=http://localhost:8080 loadtest/transfers.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 50);
const ITERATIONS = Number(__ENV.ITERATIONS || 5000);
const ACCOUNTS = Number(__ENV.ACCOUNTS || 20);
const OPENING_BALANCE = '1000.00';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Custom metrics, shown in the summary at the end of the run.
const transfersCreated = new Counter('transfers_created');
const insufficientFunds = new Counter('transfers_rejected_insufficient_funds');
const unexpectedResponses = new Counter('unexpected_responses');
const ledgerBalanced = new Rate('ledger_balanced');

// 422 (insufficient funds) is a correct answer from the API, not a failure,
// so it must not count towards http_req_failed.
http.setResponseCallback(http.expectedStatuses(200, 201, 422));

export const options = {
  scenarios: {
    random_transfers: {
      executor: 'shared-iterations',   // ITERATIONS transfers in total, shared between VUS users
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '10m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],     // fewer than 1% unexpected HTTP errors
    unexpected_responses: ['count==0'],
    ledger_balanced: ['rate==1'],       // the reconciliation at the end must pass
  },
};

function idempotencyKey(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

// Runs once before the load: create the accounts and give each an opening balance.
export function setup() {
  const accountIds = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const created = http.post(`${BASE_URL}/accounts`,
      JSON.stringify({ ownerName: `Load test ${i}`, currency: 'EUR' }), { headers: JSON_HEADERS });
    if (created.status !== 201) {
      throw new Error(`Could not create account: ${created.status} ${created.body}`);
    }
    const id = created.json('id');
    const deposit = http.post(`${BASE_URL}/accounts/${id}/deposits`,
      JSON.stringify({ amount: OPENING_BALANCE }),
      { headers: Object.assign({ 'Idempotency-Key': idempotencyKey('seed') }, JSON_HEADERS) });
    if (deposit.status !== 201) {
      throw new Error(`Could not deposit: ${deposit.status} ${deposit.body}`);
    }
    accountIds.push(id);
  }
  return { accountIds };
}

// Runs ITERATIONS times in total, on VUS virtual users in parallel.
export default function (data) {
  const ids = data.accountIds;
  const from = ids[Math.floor(Math.random() * ids.length)];
  let to = ids[Math.floor(Math.random() * ids.length)];
  while (to === from) {
    to = ids[Math.floor(Math.random() * ids.length)];
  }
  // 0.01 to 200.00, always two decimal places. Large enough that some accounts run dry,
  // so the overdraft rule is exercised under load too.
  const amount = (Math.floor(Math.random() * 20000) + 1) / 100;

  const response = http.post(`${BASE_URL}/transfers`,
    JSON.stringify({ fromAccountId: from, toAccountId: to, amount: amount.toFixed(2) }),
    { headers: Object.assign({ 'Idempotency-Key': idempotencyKey(`vu${__VU}-${__ITER}`) }, JSON_HEADERS) });

  if (response.status === 201) {
    transfersCreated.add(1);
  } else if (response.status === 422) {
    insufficientFunds.add(1);
  } else {
    unexpectedResponses.add(1);
    console.error(`Unexpected response ${response.status}: ${response.body}`);
  }
  check(response, { 'transfer is 201 or 422': (r) => r.status === 201 || r.status === 422 });
}

// Runs once after the load: ask the service whether the ledger still balances.
export function teardown(data) {
  const response = http.get(`${BASE_URL}/admin/reconciliation`);
  const report = response.json();
  console.log(`Reconciliation report: ${JSON.stringify(report)}`);

  const balanced = response.status === 200 && report.balanced === true;
  ledgerBalanced.add(balanced);
  check(report, {
    'ledger is balanced': (r) => r.balanced === true,
    'ledger total is zero': (r) => Number(r.ledgerTotal) === 0,
    'no mismatched accounts': (r) => r.mismatches.length === 0,
  });

  // No test account may have gone negative.
  for (const id of data.accountIds) {
    const account = http.get(`${BASE_URL}/accounts/${id}`).json();
    check(account, { 'balance is not negative': (a) => Number(a.balance) >= 0 });
  }
}
