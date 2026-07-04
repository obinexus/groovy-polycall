#include "groovy_polycall.h"
#include "polycall/polycall_ffi.h"

int groovy_polycall_run_config(const char *config_path) {
    return polycall_ffi_run_config(config_path, 1);
}
