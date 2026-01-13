BOOT_JARS="/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar"
dalvikvm64 -Xmx5120m -Xusejit:true -Xnoimage-dex2oat -XX:DumpNativeStackOnSigQuit:true -Xcompiler-option --compiler-filter=everything -Xcompiler-option -Xjitcodecounters -Xcompiler-option --generate-debug-info -verbose:jit -Xjitthreshold:0 -Xcompiler-option --dump-cfg=/data/local/tmp/cfg_dumps \
  -Xbootclasspath:"${BOOT_JARS}" \
  -Xbootclasspath-locations:"${BOOT_JARS}" \
  -cp /data/local/tmp/AllTests.dex AllTests --full


BOOT_JARS="/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar"
dalvikvm64 -Xmx5120m -Xusejit:true -Xnoimage-dex2oat -XX:DumpNativeStackOnSigQuit:true -Xcompiler-option --compiler-filter=everything -Xcompiler-option -Xjitcodecounters -Xcompiler-option --generate-debug-info -verbose:jit -Xjitthreshold:0 \
  -Xbootclasspath:"${BOOT_JARS}" \
  -Xbootclasspath-locations:"${BOOT_JARS}" \
  -cp /data/local/tmp/GcRootStackMapTest.dex GcRootStackMapTest main 


BOOT_JARS="/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar"
dalvikvm64  -Xuse-stderr-logger  -Xmx5120m -Xusejit:true -Xnoimage-dex2oat -XX:DumpNativeStackOnSigQuit:true -Xcompiler-option --compiler-filter=everything -Xcompiler-option -Xjitcodecounters -Xcompiler-option --generate-debug-info -verbose:jit -Xjitthreshold:0 \
  -Xbootclasspath:"${BOOT_JARS}" \
  -Xbootclasspath-locations:"${BOOT_JARS}" \
  -cp /data/local/tmp/StackMapConstTest.dex StackMapConstTest main 


VM_FLAGS="${-Xmx5120m -Xusejit:false -Xint -Xnoimage-dex2oat -XX:DumpNativeStackOnSigQuit:true -Xcompiler-option --compiler-filter=everything -Xcompiler-option -Xjitcodecounters -Xcompiler-option --generate-debug-info -verbose:jit -Xjitthreshold:1}"

BOOT_JARS="/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar"

dalvikvm64 \
  -Xbootclasspath:"${BOOT_JARS}" \
  -Xbootclasspath-locations:"${BOOT_JARS}" \
  ${VM_FLAGS} \
  -cp /data/local/tmp/BytecodePlayground.dex BytecodePlayground main




