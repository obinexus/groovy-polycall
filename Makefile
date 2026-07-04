CC ?= gcc
AR ?= ar
GROOVY ?= groovy
GROOVYC ?= groovyc

CPPFLAGS ?=
CPPFLAGS += -Iinclude -Igenerated
CFLAGS ?= -O2
CFLAGS += -std=c11 -Wall -Wextra -Wpedantic
JNI_CFLAGS ?= -fPIC

BUILD_DIR := build
CLASSES_DIR := $(BUILD_DIR)/classes
LIB_DIR := lib
ADAPTER_OBJ := $(BUILD_DIR)/groovy_polycall.o
STATIC_LIB := $(LIB_DIR)/libgroovy_polycall.a
TEST_BIN := $(BUILD_DIR)/groovy_polycall_adapter_test
GROOVY_SOURCES := $(wildcard src/main/groovy/org/obinexus/polycall/*.groovy)

ifeq ($(OS),Windows_NT)
EXE_EXT := .exe
JNI_PLATFORM := win32
JNI_LIB := $(LIB_DIR)/groovy_polycall.dll
MOCK_JNI_LIB := $(BUILD_DIR)/groovy_polycall_mock.dll
TEST_BIN := $(TEST_BIN)$(EXE_EXT)
else
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
JNI_PLATFORM := darwin
JNI_LIB := $(LIB_DIR)/libgroovy_polycall.dylib
MOCK_JNI_LIB := $(BUILD_DIR)/libgroovy_polycall_mock.dylib
else
JNI_PLATFORM := linux
JNI_LIB := $(LIB_DIR)/libgroovy_polycall.so
MOCK_JNI_LIB := $(BUILD_DIR)/libgroovy_polycall_mock.so
endif
endif

.DEFAULT_GOAL := all

.PHONY: all
all: $(STATIC_LIB)

$(BUILD_DIR) $(CLASSES_DIR) $(LIB_DIR):
ifeq ($(OS),Windows_NT)
	@if not exist "$@" mkdir "$@"
else
	@mkdir -p $@
endif

$(ADAPTER_OBJ): c_src/groovy_polycall.c include/groovy_polycall.h generated/polycall/polycall_ffi.h | $(BUILD_DIR)
	$(CC) $(CPPFLAGS) $(CFLAGS) -MMD -MP -c $< -o $@

$(STATIC_LIB): $(ADAPTER_OBJ) | $(LIB_DIR)
	$(AR) rcs $@ $^

$(TEST_BIN): c_src/groovy_polycall.c tests/polycall_ffi_mock.c tests/groovy_polycall_adapter_test.c | $(BUILD_DIR)
	$(CC) $(CPPFLAGS) -Itests $(CFLAGS) $^ -o $@

.PHONY: test
test: $(TEST_BIN)
	$(TEST_BIN)

.PHONY: jni
jni: | $(LIB_DIR)
ifeq ($(OS),Windows_NT)
	@if "$(strip $(JAVA_HOME))"=="" (echo Set JAVA_HOME to a JDK installation & exit /b 2)
	@if "$(strip $(POLYCALL_LDFLAGS))"=="" (echo Set POLYCALL_LDFLAGS to the libpolycall v1.5 linker flags & exit /b 2)
else
	@test -n "$(JAVA_HOME)" || (echo "Set JAVA_HOME to a JDK installation" && exit 2)
	@test -n "$(POLYCALL_LDFLAGS)" || (echo "Set POLYCALL_LDFLAGS to the libpolycall v1.5 linker flags" && exit 2)
endif
	$(CC) $(CPPFLAGS) -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/$(JNI_PLATFORM)" \
		$(CFLAGS) $(JNI_CFLAGS) -shared c_src/groovy_polycall.c \
		c_src/groovy_polycall_jni.c $(POLYCALL_LDFLAGS) -o $(JNI_LIB)

$(MOCK_JNI_LIB): c_src/groovy_polycall.c c_src/groovy_polycall_jni.c tests/polycall_ffi_mock.c | $(BUILD_DIR)
ifeq ($(OS),Windows_NT)
	@if "$(strip $(JAVA_HOME))"=="" (echo Set JAVA_HOME to a JDK installation & exit /b 2)
else
	@test -n "$(JAVA_HOME)" || (echo "Set JAVA_HOME to a JDK installation" && exit 2)
endif
	$(CC) $(CPPFLAGS) -Itests -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/$(JNI_PLATFORM)" \
		$(CFLAGS) $(JNI_CFLAGS) -shared c_src/groovy_polycall.c \
		c_src/groovy_polycall_jni.c tests/polycall_ffi_mock.c -o $@

.PHONY: test-groovy
test-groovy: $(MOCK_JNI_LIB) | $(CLASSES_DIR)
	$(GROOVYC) -d $(CLASSES_DIR) $(GROOVY_SOURCES)
	$(GROOVY) -cp $(CLASSES_DIR) \
		-Dgroovy.polycall.library="$(abspath $(MOCK_JNI_LIB))" \
		tests/groovy_polycall_smoke.groovy

.PHONY: verify-dry
verify-dry:
ifeq ($(OS),Windows_NT)
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-dry.ps1
else
	sh scripts/verify-dry.sh
endif

.PHONY: clean
clean:
ifeq ($(OS),Windows_NT)
	@if exist "$(BUILD_DIR)" rmdir /s /q "$(BUILD_DIR)"
	@if exist "$(LIB_DIR)" rmdir /s /q "$(LIB_DIR)"
else
	rm -rf $(BUILD_DIR) $(LIB_DIR)
endif

-include $(ADAPTER_OBJ:.o=.d)
