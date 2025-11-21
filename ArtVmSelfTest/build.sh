javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes ArtVmSelfTest.java


jar cf out/ArtVmSelfTest.jar -C out/classes .


~/nvme/aosp15/out/host/linux-x86/bin/d8 --release --min-api 21 --output dex ./out/ArtVmSelfTest.jar 

cd dex 
mv classes.dex ArtVmSelfTest.dex
adb push ArtVmSelfTest.dex /data/local/tmp/