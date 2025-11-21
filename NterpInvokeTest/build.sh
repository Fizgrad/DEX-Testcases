TARGET=NterpInvokeTest

mkdir -p out/classes
mkdir -p dex

javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes ${TARGET}.java


jar cf out/${TARGET}.jar -C out/classes .

~/Sdk/build-tools/36.1.0/d8  --release --min-api 35 --output dex ./out/${TARGET}.jar 

cd dex 
mv classes.dex ${TARGET}.dex
adb push ${TARGET}.dex /data/local/tmp/