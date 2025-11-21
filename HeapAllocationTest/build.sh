javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes HeapAllocationTest.java


jar cf out/HeapAllocationTest.jar -C out/classes .


~/nvme/aosp15/out/host/linux-x86/bin/d8 --release --min-api 21 --output dex ./out/HeapAllocationTest.jar 

cd dex 
mv classes.dex HeapAllocationTest.dex
adb push HeapAllocationTest.dex /data/local/tmp/