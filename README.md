# LatLongToTimezone
Lat/long to timezone mapper in Java and Swift and C#. Does not require web services or data files. 

The "lat/long to timezone polygon mapping" is hardcoded, and we hope this rarely changes, but the changes to offsets and daylight savings changeover dates etc. (which are more frequent) are taken care of by your system libraries and so these are automatically kept up-to-date.  From time to time, someone updates the files with the latest timezone polygons, but these rarely change...I think the most recent change is the Crimean peninsular.

99% of people using this project just need the one file:

(Java)
https://github.com/drtimcooper/LatLongToTimezone/blob/master/src/main/java/com/skedgo/converter/TimezoneMapper.java

(Swift)
https://github.com/drtimcooper/LatLongToTimezone/blob/master/Classes/TimezoneMapper.swift

(CSharp)
https://github.com/drtimcooper/LatLongToTimezone/blob/master/Output/Toolbox.TimeAndDate.TimezoneMapper.cs

(golang)
https://github.com/zsefvlol/timezonemapper

# Install 

### [CocoaPods](https://guides.cocoapods.org/using/using-cocoapods.html)

```
# Podfile
use_frameworks!

pod 'LatLongToTimezone', '~> 1.1'

```

In the `Podfile` directory, type:

```
$ pod install
```

### [Carthage](https://github.com/Carthage/Carthage)

Add this to `Cartfile`

```
github "drtimcooper/LatLongToTimezone" ~> 1.1
```

```
$ carthage update
```

### Swift Package Manager (SPM)

Add ```https://github.com/drtimcooper/LatLongToTimezone``` to your Swift packages in Xcode.

# Versions

For Swift 2.3 and earlier, use version 1.0.4 of the Podspec.
For Swift 3 to 4.1, use version 1.1.3 of the Podspec.
For Swift 4.2 or later, use the latest version.

# Usage

In your code, you can do

```Swift
import LatLongToTimezone

let location = CLLocationCoordinate2D(latitude: 34, longitude: -122)
let timeZone = TimezoneMapper.latLngToTimezone(location)

```

# Android Studio (Kotlin)

- Create a Java class calls `TimezoneMapper`
- Copy & Paste [this file](https://raw.githubusercontent.com/drtimcooper/LatLongToTimezone/master/src/main/java/com/skedgo/converter/TimezoneMapper.java)
- Remove `package com.skedgo.converter;` and replace it with your package (Don't forget the **semicolon**)

Add in your `Activity`:

    val resultTimeZone = TimezoneMapper.latLngToTimezoneString(YOUR_LATITUDE, YOUR_LONGITUDE)
    Log.i("", resultTimeZone)

Now you should see the TimeZone (open `Logcat`) 
