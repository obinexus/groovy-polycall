'use strict';

// npm package integrity: the entry point loads and every path it exports
// exists in the (packed or checked-out) package.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const binding = require('..');
const metadata = require('../package.json');
const manifest = require('../polycall-binding.json');

assert.equal(metadata.name, '@obinexusltd/groovy-polycall');
assert.equal(metadata.license, 'MIT');
assert.equal(metadata.author.name, 'Nnamdi Michael Okpala');
assert.equal(metadata.publishConfig.access, 'public');
assert.equal(metadata.repository.url, 'git+https://github.com/obinexus/groovy-polycall.git');
assert.equal(manifest.version, metadata.version, 'polycall-binding.json version matches package.json');
assert.equal(manifest.core, 'polycall >= 1.1.0 (binding ABI 1)');
assert.equal(manifest.core_repository, 'https://github.com/obinexus/polycall');

const gradle = fs.readFileSync(binding.gradleBuild, 'utf8');
assert.ok(gradle.includes(`version = '${metadata.version}'`), 'build.gradle version matches package.json');

for (const source of binding.groovySources) {
  assert.equal(fs.existsSync(source), true, `missing Groovy source: ${source}`);
}
for (const [name, file] of Object.entries(binding)) {
  if (typeof file !== 'string' || !path.isAbsolute(file)) continue;
  assert.equal(fs.existsSync(file), true, `missing ${name}: ${file}`);
}

console.log('groovy-polycall npm package test: PASS');
