'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const binding = require('..');
const metadata = require('../package.json');

assert.equal(metadata.name, '@obinexusltd/groovy-polycall');
assert.equal(metadata.license, 'MIT');
assert.equal(metadata.author.name, 'Nnamdi Michael Okpala');
assert.equal(metadata.publishConfig.access, 'public');

for (const source of binding.groovySources) {
  assert.equal(fs.existsSync(source), true, `missing Groovy source: ${source}`);
}

for (const [name, file] of Object.entries(binding)) {
  if (name === 'packageName' || name === 'groovySources') continue;
  assert.equal(fs.existsSync(file), true, `missing ${name}: ${file}`);
}

console.log('groovy-polycall npm package test: PASS');
