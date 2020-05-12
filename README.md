# SourceAFIS Port for Android

[SourceAFIS](https://sourceafis.machinezoo.com/) project ported from https://github.com/LintraMax/SourceAFIS-Android to run on Android.
This is version `3.10.0` of original SourceAFIS Java version.

## Warning
`FingerprintImage.java` and `ImageDecoder.java` are not ported due its complexity, and we don't need it.

# Installation

You can get the latest build from Jitpack Maven repository.

```gradle
   allprojects {
        repositories {
            jcenter()
            maven { url "https://jitpack.io" }
        }
   }
   dependencies {
        implementation 'com.github.LintraMax:SourceAFIS-Android:v3.10.0'
   }
```
