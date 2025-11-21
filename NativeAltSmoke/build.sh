javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes NativeAltSmoke.java


jar cf out/NativeAltSmoke.jar -C out/classes .


~/nvme/aosp15/out/host/linux-x86/bin/d8 --release --min-api 21 --output dex ./out/NativeAltSmoke.jar 

cd dex 
mv classes.dex NativeAltSmoke.dex
adb push NativeAltSmoke.dex /data/local/tmp/

#  /data/local/tmp/art_wrap/bin/dalvikvm64 -Xgc:CC -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/NativeAltSmoke.dex NativeAltSmoke main