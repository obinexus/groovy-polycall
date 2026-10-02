/*
 * TEST FIXTURE ONLY -- NOT libpolycall.
 *
 * A fake library that exports every Binding ABI v1 symbol but reports
 * polycall_ffi_abi_version() == 2. It exists solely so the binding's loader
 * test can prove that an ABI mismatch is refused with a clear error instead
 * of being used. No test exercises the Polycall API through this file.
 *
 *   cc -shared -fPIC -o libfake_polycall_abi2.so src/test/c/fake_polycall_abi2.c
 *   gcc -shared -o fake_polycall_abi2.dll src/test/c/fake_polycall_abi2.c   (MinGW)
 */
#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define FAKE_API __declspec(dllexport)
#else
#define FAKE_API __attribute__((visibility("default")))
#endif

#define FAKE_FAIL (-18) /* POLYCALL_E_INTERNAL */

FAKE_API int polycall_ffi_abi_version(void) { return 2; }
FAKE_API int polycall_ffi_version(char *b, int n) { (void)b; (void)n; return FAKE_FAIL; }
FAKE_API const char *polycall_strerror(int s) { (void)s; return "FAKE"; }
FAKE_API int polycall_last_error(char *b, size_t n) { if (b && n) b[0] = 0; return 0; }
FAKE_API const char *polycall_get_version(void) { return "fake"; }
FAKE_API int polycall_ffi_run_config(const char *p, int r) { (void)p; (void)r; return FAKE_FAIL; }
FAKE_API int polycall_ffi_describe(const char *p, char *b, int n) { (void)p; (void)b; (void)n; return FAKE_FAIL; }
FAKE_API int polycall_call(const char *e, const char *s, const char *o, const char *i, uint32_t t,
                           char *out, size_t cap, size_t *len)
{ (void)e; (void)s; (void)o; (void)i; (void)t; (void)out; (void)cap; (void)len; return FAKE_FAIL; }
FAKE_API int polycall_peer_open(const char *n, const char *b, const char *a, int32_t *h)
{ (void)n; (void)b; (void)a; (void)h; return FAKE_FAIL; }
FAKE_API int polycall_peer_close(int32_t h) { (void)h; return FAKE_FAIL; }
FAKE_API int polycall_peer_endpoint(int32_t h, char *b, size_t n) { (void)h; (void)b; (void)n; return FAKE_FAIL; }
FAKE_API int polycall_peer_node_id(int32_t h, char *b, size_t n) { (void)h; (void)b; (void)n; return FAKE_FAIL; }
FAKE_API int polycall_peer_register(int32_t h, const char *i, const char *e) { (void)h; (void)i; (void)e; return FAKE_FAIL; }
FAKE_API int polycall_peer_unregister(int32_t h, const char *i) { (void)h; (void)i; return FAKE_FAIL; }
FAKE_API int polycall_peer_list(int32_t h, char *b, size_t n, size_t *l) { (void)h; (void)b; (void)n; (void)l; return FAKE_FAIL; }
FAKE_API int polycall_peer_ping(int32_t h, const char *p, uint32_t t) { (void)h; (void)p; (void)t; return FAKE_FAIL; }
FAKE_API int polycall_peer_send(int32_t h, const char *p, const void *d, size_t n, const char *m, uint32_t t)
{ (void)h; (void)p; (void)d; (void)n; (void)m; (void)t; return FAKE_FAIL; }
FAKE_API int polycall_peer_recv(int32_t h, uint32_t t, char *s, size_t sc, char *m, size_t mc,
                                void *p, size_t pc, size_t *pl)
{ (void)h; (void)t; (void)s; (void)sc; (void)m; (void)mc; (void)p; (void)pc; (void)pl; return FAKE_FAIL; }
FAKE_API int polycall_peer_cancel(int32_t h) { (void)h; return FAKE_FAIL; }
FAKE_API int polycall_peer_health(int32_t h, char *b, size_t n, size_t *l) { (void)h; (void)b; (void)n; (void)l; return FAKE_FAIL; }
