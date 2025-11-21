javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes NullBytecodeSamples.java

mkdir -p out/classes
jar cf out/NullBytecodeSamples.jar -C out/classes .


~/nvme/aosp15/out/host/linux-x86/bin/d8 --release --min-api 21 --output dex ./out/NullBytecodeSamples.jar 

cd dex 
mv classes.dex NullBytecodeSamples.dex
# adb push NullBytecodeSamples.dex /data/local/tmp/