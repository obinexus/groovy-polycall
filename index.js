'use strict';

const path = require('node:path');

const fromPackageRoot = (...parts) => path.join(__dirname, ...parts);

module.exports = Object.freeze({
  packageName: '@obinexusltd/groovy-polycall',
  groovySources: Object.freeze([
    fromPackageRoot('src', 'main', 'groovy', 'org', 'obinexus', 'polycall', 'Polycall.groovy'),
    fromPackageRoot('src', 'main', 'groovy', 'org', 'obinexus', 'polycall', 'PolycallException.groovy')
  ]),
  nativeSource: fromPackageRoot('c_src', 'groovy_polycall.c'),
  jniSource: fromPackageRoot('c_src', 'groovy_polycall_jni.c'),
  nativeHeader: fromPackageRoot('include', 'groovy_polycall.h'),
  ffiHeader: fromPackageRoot('generated', 'polycall', 'polycall_ffi.h'),
  config: fromPackageRoot('groovy-polycallrc'),
  manifest: fromPackageRoot('polycall-binding.json'),
  makefile: fromPackageRoot('Makefile'),
  gradleBuild: fromPackageRoot('build.gradle')
});
