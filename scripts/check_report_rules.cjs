// Run only under the Firebase emulator; never writes to a real project.
const assert = require('node:assert/strict');
(async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  assert(host && /^(127\.0\.0\.1|localhost):\d+$/.test(host), 'Local emulator required');
  const baseUrl = `http://${host}/v1`;
  const dbPath = 'projects/demo-chevere-release/databases/(default)/documents';
  const root = `${baseUrl}/${dbPath}`;

  // 1. Basic access checks (unvalidated POST to ai_content_reports must be rejected)
  for (const [path, method, status] of [
    ['models', 'GET', 200],
    ['models', 'POST', 403],
    ['ai_content_reports', 'GET', 403],
    ['ai_content_reports', 'POST', 403],
    ['private_data', 'GET', 403],
  ]) {
    const response = await fetch(`${root}/${path}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      ...(method === 'POST' ? { body: JSON.stringify({ fields: { reason: { stringValue: 'synthetic emulator test' } } }) } : {}),
    });
    assert.equal(response.status, status, `${method} ${path}`);
    console.log(`${method} ${path}: ${status}`);
  }

  // 2. Valid create with serverTimestamp + 90-day expiresAt via ":commit"
  const expiresAtIso = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString();
  const reportDocPath = `${dbPath}/ai_content_reports/test-report-valid-1`;
  const validCommitResponse = await fetch(`${baseUrl}/${dbPath}:commit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      writes: [
        {
          currentDocument: { exists: false },
          update: {
            name: reportDocPath,
            fields: {
              reason: { stringValue: 'Inaccurate or unsafe response' },
              modelName: { stringValue: 'gemma-4-E2B-it-web.litertlm' },
              messageSnippet: { stringValue: 'Scrubbed message excerpt for emulator test.' },
              details: { stringValue: 'Optional user notes.' },
              appVersion: { stringValue: '1.0.0' },
              expiresAt: { timestampValue: expiresAtIso },
            },
          },
          updateTransforms: [
            {
              fieldPath: 'createdAt',
              setToServerValue: 'REQUEST_TIME',
            },
          ],
        },
      ],
    }),
  });
  assert.equal(validCommitResponse.status, 200, 'Valid report create commit');
  console.log('COMMIT valid ai_content_reports create: 200');

  // 3. Verify read, update, and delete on the created report are denied
  for (const [method, expected] of [
    ['GET', 403],
    ['PATCH', 403],
    ['DELETE', 403],
  ]) {
    const res = await fetch(`${root}/ai_content_reports/test-report-valid-1`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      ...(method === 'PATCH' ? { body: JSON.stringify({ fields: { reason: { stringValue: 'tampered' } } }) } : {}),
    });
    assert.equal(res.status, expected, `${method} existing ai_content_reports`);
    console.log(`${method} ai_content_reports/test-report-valid-1: ${res.status}`);
  }

  // 4. Verify invalid retention window (e.g., 365 days) is rejected
  const badExpiresIso = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString();
  const invalidTtlResponse = await fetch(`${baseUrl}/${dbPath}:commit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      writes: [
        {
          currentDocument: { exists: false },
          update: {
            name: `${dbPath}/ai_content_reports/test-report-bad-ttl`,
            fields: {
              reason: { stringValue: 'Unsafe' },
              modelName: { stringValue: 'model' },
              messageSnippet: { stringValue: 'Snippet' },
              details: { stringValue: '' },
              appVersion: { stringValue: '1.0.0' },
              expiresAt: { timestampValue: badExpiresIso },
            },
          },
          updateTransforms: [
            {
              fieldPath: 'createdAt',
              setToServerValue: 'REQUEST_TIME',
            },
          ],
        },
      ],
    }),
  });
  assert.equal(invalidTtlResponse.status, 403, 'Invalid TTL window must be rejected');
  console.log('COMMIT invalid TTL ai_content_reports create: 403');
})().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
