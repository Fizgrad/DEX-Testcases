#!/usr/bin/env bash
# Build unified AllTests dex (dex/AllTests/AllTests.dex) and run on device.
# Pass args through to AllTests, e.g. --include=stress --full --only=Bytecode.
set -euo pipefail

ADB="${ADB:-adb}"
DEX_REMOTE="${DEX_REMOTE:-/data/local/tmp}"
VM_FLAGS="${VM_FLAGS:--Xmx5120m -Xusejit:true -Xnoimage-dex2oat -XX:DumpNativeStackOnSigQuit:true -Xcompiler-option --compiler-filter=everything -Xcompiler-option -Xjitcodecounters -Xcompiler-option --generate-debug-info -verbose:jit -Xjitthreshold:0}"
BOOT_JARS="/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar"

include_stress=false
for arg in "$@"; do
  if [[ "$arg" == "--include=stress" || "$arg" == "--include=all" ]]; then
    include_stress=true
  fi
done

echo "== Building AllTests =="
make AllTests

dex_local="dex/AllTests/AllTests.dex"
dex_remote="${DEX_REMOTE}/AllTests.dex"

if [[ ! -f "${dex_local}" ]]; then
  echo "[FAIL] AllTests dex not found at ${dex_local}"
  exit 1
fi

echo "== Pushing ${dex_local} -> ${dex_remote} =="
"${ADB}" push "${dex_local}" "${dex_remote}" >/dev/null

if [[ "${include_stress}" == "true" ]]; then
  echo "== Building native library for NativeInteropTest =="
  make NativeInteropTest-native
  echo "== Pushing native library =="
  "${ADB}" push "out/NativeInteropTest/native/libartnativetest.so" "${DEX_REMOTE}/libartnativetest.so" >/dev/null
fi

env_prefix=""
if [[ "${include_stress}" == "true" ]]; then
  env_prefix="LD_LIBRARY_PATH=${DEX_REMOTE}:\$LD_LIBRARY_PATH"
fi

"${ADB}" shell ${env_prefix:+${env_prefix} }dalvikvm64 \
  -Xbootclasspath:"${BOOT_JARS}" \
  -Xbootclasspath-locations:"${BOOT_JARS}" \
  ${VM_FLAGS} \
  -cp "${dex_remote}" AllTests "$@"
