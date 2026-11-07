# Third-party asset notices

## Vendored Material Icons vectors

The `ic_filled_*_24.xml`, `ic_outlined_*_24.xml`, and
`ic_automirrored_rounded_*_24.xml` drawables added in this size-reduction batch derive from
AndroidX Material Icons 1.7.8 (`material-icons-core-android` and
`material-icons-extended-android`). The original Kotlin sources are distributed through
[Google Maven](https://maven.google.com/web/index.html#androidx.compose.material).
The upstream project is [AndroidX](https://android.googlesource.com/platform/frameworks/support/).

Copyright 2025 The Android Open Source Project. Licensed under the Apache License, Version 2.0.
The Kotlin vector paths were converted to Android XML vector drawables. The original path
commands, 24 dp dimensions, 24-unit viewports, path ordering, black fill, and automatic RTL
mirroring were retained. Existing Compose call sites continue to apply their original tint,
size and accessibility descriptions.

The full license, attribution and per-app variant lists are included in the distributed apps:

- `phone/src/main/assets/licenses/material-icons.txt`
- `watch/src/main/assets/licenses/material-icons.txt`

Phone assets: Filled FlashlightOff, FlashlightOn, MoreVert, MusicNote, MusicOff, Pause,
PlayArrow, RadioButtonChecked, RadioButtonUnchecked and Sos; Outlined Watch.

Watch assets: AutoMirrored Rounded VolumeDown and VolumeUp; Filled MusicNote, MusicOff,
Pause, PlayArrow and Sos; Outlined FlashlightOn, PhoneAndroid and PhonelinkErase.

This document covers these newly vendored assets. Other dependencies retain their own licenses.
