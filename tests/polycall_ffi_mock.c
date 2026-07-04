#include "polycall_ffi_mock.h"

#include <stdlib.h>
#include <string.h>

static int mock_status;
static int mock_calls;
static int mock_run;
static char mock_config[1024];

int polycall_ffi_run_config(const char *config_path, int run) {
    const char *expected = getenv("POLYCALL_EXPECT_CONFIG");
    const char *status_text = getenv("POLYCALL_MOCK_STATUS");

    ++mock_calls;
    mock_run = run;

    if (config_path) {
        strncpy(mock_config, config_path, sizeof(mock_config) - 1);
        mock_config[sizeof(mock_config) - 1] = '\0';
    } else {
        mock_config[0] = '\0';
    }

    if (run != 1) {
        return 92;
    }
    if (expected && strcmp(mock_config, expected) != 0) {
        return 91;
    }
    if (strcmp(mock_config, "__status_37__") == 0) {
        return 37;
    }
    if (status_text) {
        return atoi(status_text);
    }
    return mock_status;
}

void polycall_ffi_mock_reset(void) {
    mock_status = 0;
    mock_calls = 0;
    mock_run = 0;
    mock_config[0] = '\0';
}

void polycall_ffi_mock_return_status(int status) {
    mock_status = status;
}

int polycall_ffi_mock_call_count(void) {
    return mock_calls;
}

int polycall_ffi_mock_last_run(void) {
    return mock_run;
}

const char *polycall_ffi_mock_last_config(void) {
    return mock_config;
}
