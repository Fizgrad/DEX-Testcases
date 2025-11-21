cd /home/david/aosp14/
source ./build/envsetup.sh
lunch aosp_cf_x86_64_phone-userdebug
/home/david/aosp14/out_origin/host/linux-x86/bin/dalvikvm  -Xbootclasspath:/home/david/aosp14/out/host/linux-x86/apex/com.android.art/javalib/apache-xml.jar:/home/david/aosp14/out/host/linux-x86/apex/com.android.art/javalib/bouncycastle.jar:/home/david/aosp14/out/host/linux-x86/apex/com.android.art/javalib/core-libart.jar:/home/david/aosp14/out/host/linux-x86/apex/com.android.art/javalib/core-oj.jar:/home/david/aosp14/out/host/linux-x86/apex/com.android.art/javalib/okhttp.jar -verbose:startup -cp /home/david/TestJava/classes.dex  HeapAllocationTest




dalvikvm64  -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/BytecodeExercise.dex BytecodeExercise main 



dalvikvm64 -Xuse-stderr-logger -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/HeapAllocationTest.dex HeapAllocationTest main 


dalvikvm64 -Xuse-stderr-logger -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/HelloWorldTest.dex HelloWorldTest main 



dalvikvm64 -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/ArtVmSelfTest.dex ArtVmSelfTest main 

ArtVmSelfTest.dex
HelloWorldTest