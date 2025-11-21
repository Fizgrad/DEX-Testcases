javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes BytecodeExercise.java


jar cf out/bytecode-exercise.jar -C out/classes .


~/nvme/aosp15/out/host/linux-x86/bin/d8 --release --min-api 21 --output dex ./out/bytecode-exercise.jar 

cd dex 
mv classes.dex BytecodeExercise.dex
adb push BytecodeExercise.dex /data/local/tmp/