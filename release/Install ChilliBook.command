#!/bin/zsh
set -u

release_dir=${0:A:h}
packages=("$release_dir"/ChilliBook-[0-9]*.apk(NnOn))
if (( ${#packages[@]} == 0 )); then
    print -u2 "Keep this installer beside a versioned ChilliBook release APK."
    exit 1
fi

sdk_dir=${ANDROID_HOME:-"$HOME/Library/Android/sdk"}
adb="$sdk_dir/platform-tools/adb"
if [[ ! -x "$adb" ]]; then
    print -u2 "Install Android SDK Platform-Tools in Android Studio, then open this installer again."
    exit 1
fi

print "Installing ${packages[1]:t} on your USB-connected phone…"
if "$adb" -d install -r "${packages[1]}"; then
    print "Installed. Open ChilliBook on your phone."
else
    print -u2 "Check the message above. For a connection error, unlock your phone, enable USB debugging and accept the USB debugging prompt. Then double-click this installer again."
    exit 1
fi
