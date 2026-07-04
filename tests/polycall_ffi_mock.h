#ifndef POLYCALL_FFI_MOCK_H
#define POLYCALL_FFI_MOCK_H

void polycall_ffi_mock_reset(void);
void polycall_ffi_mock_return_status(int status);
int polycall_ffi_mock_call_count(void);
int polycall_ffi_mock_last_run(void);
const char *polycall_ffi_mock_last_config(void);

#endif
