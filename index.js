'use strict';

// groovy-polycall is a source distribution of a Groovy binding;
// requiring it from Node.js only locates the packaged files.
const path = require('node:path');

const fromPackageRoot = (...parts) => path.join(__dirname, ...parts);
const source = (name) => fromPackageRoot('src', 'main', 'groovy', 'org', 'obinexus', 'polycall', name);

module.exports = Object.freeze({
  packageName: 'groovy-polycall',
  language: 'Groovy',
  abi: 1,
  groovySources: Object.freeze([
    source('Polycall.groovy'),
    source('PolycallException.groovy'),
    source('Peer.groovy'),
    source('NativeApi.groovy')
  ]),
  config: fromPackageRoot('groovy-polycallrc'),
  manifest: fromPackageRoot('polycall-binding.json'),
  gradleBuild: fromPackageRoot('build.gradle')
});
