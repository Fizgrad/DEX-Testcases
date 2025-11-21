# Compile and deploy Java program to Android device


TARGET=YOUR_JAVA_PROGRAM_NAME_HERE

javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes ${TARGET}.java


jar cf out/${TARGET}.jar -C out/classes .


~/nvme/aosp15/out/host/linux-x86/bin/d8 --release --min-api 21 --output dex ./out/${TARGET}.jar

cd dex
mv classes.dex ${TARGET}.dex
adb push ${TARGET}.dex /data/local/tmp/
