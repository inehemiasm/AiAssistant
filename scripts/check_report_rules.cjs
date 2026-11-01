// Run only under the Firebase emulator; never writes to a real project.
const assert = require('node:assert/strict');
(async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  assert(host && /^(127\.0\.0\.1|localhost):\d+$/.test(host), 'Local emulator required');
  const root = `http://${host}/v1/projects/demo-chevere-release/databases/(default)/documents`;
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
      ...(method === 'POST' ? {body: JSON.stringify({fields:{reason:{stringValue:'synthetic emulator test'}}})} : {}),
    });
    assert.equal(response.status, status, `${method} ${path}`);
    console.log(`${method} ${path}: ${status}`);
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
