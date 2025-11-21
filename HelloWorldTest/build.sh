javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes HelloWorldTest.java


jar cf out/HelloWorldTest.jar -C out/classes .


~/nvme/aosp15/out/host/linux-x86/bin/d8 --release --min-api 21 --output dex ./out/HelloWorldTest.jar 

cd dex 
mv classes.dex HelloWorldTest.dex
adb push HelloWorldTest.dex /data/local/tmp/