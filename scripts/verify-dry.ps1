$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$nativeSources = @(
    (Join-Path $root 'c_src/groovy_polycall.c'),
    (Join-Path $root 'c_src/groovy_polycall_jni.c')
)
$forbidden = 'fopen|open\(|CreateFile|sscanf|strtok|socket\(|connect\('
$matches = Select-String -Path $nativeSources -Pattern $forbidden

if ($matches) {
    $matches | ForEach-Object { Write-Error $_.Line }
    throw 'groovy-polycall must not parse configuration or implement runtime logic'
}

$adapter = Get-Content -Raw (Join-Path $root 'c_src/groovy_polycall.c')
$jni = Get-Content -Raw (Join-Path $root 'c_src/groovy_polycall_jni.c')
if (-not $adapter.Contains('polycall_ffi_run_config(config_path, 1)')) {
    throw 'groovy-polycall does not forward through polycall_ffi_run_config'
}
if (-not $jni.Contains('GetStringUTFChars')) {
    throw 'groovy-polycall does not marshal the Groovy string through JNI'
}
if (-not $jni.Contains('ReleaseStringUTFChars')) {
    throw 'groovy-polycall does not release the marshalled JNI string'
}

Write-Output 'groovy-polycall thin-adapter check: PASS'
