# Groovy tests

`npm test` runs the native forwarding test, thin-adapter audit, and npm package
integrity test. `npm run test:groovy` additionally compiles the Groovy API and
runs it through a mock JNI library; that target requires Groovy and a JDK whose
architecture matches the native compiler.
