# Edit these to match your local SDK/NDK layout. Override per build if you prefer.

ANDROID_SDK ?=~/Sdk
ANDROID_NDK ?=~/ndk/android-ndk-r27d

# SDK build-tools d8. If ANDROID_SDK is unset, falls back to `d8`.
BUILD_TOOLS_VERSION ?= 36.1.0
SDK_D8 ?= $(if $(ANDROID_SDK),$(ANDROID_SDK)/build-tools/$(BUILD_TOOLS_VERSION)/d8,d8)

# Android platform jar selection for Native
PLATFORM_API ?= android-36.1
