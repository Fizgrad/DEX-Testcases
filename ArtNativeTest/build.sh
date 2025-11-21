export NDK=/home/david/ndk/android-ndk-r27d
cmake -S . -B build \
  -D CMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -D ANDROID_ABI=arm64-v8a \
  -D ANDROID_PLATFORM=android-29
cmake --build build -j

mkdir -p out/classes
mkdir -p out/dex
javac -encoding UTF-8 -source 1.8 -target 1.8 \
  -d out/classes src/main/java/com/example/artnative/ArtNativeTest.java

jar cf out/ArtNativeTest.jar -C out/classes .

export ANDROID_SDK=/home/david/Sdk

BUILD_TOOLS=$ANDROID_SDK/build-tools/36.1.0
$BUILD_TOOLS/d8 --release \
  --lib $ANDROID_SDK/platforms/android-36.1/android.jar \
  --output out/dex  out/ArtNativeTest.jar