# LatLongToTimezone
Lat/long to timezone mapper in Java and Swift. Does not require web services or data files.

99% of people using this project just need the one file:

(Java)
https://github.com/drtimcooper/LatLongToTimezone/blob/master/Tester/src/TimezoneMapper.java

(Swift)
https://github.com/drtimcooper/LatLongToTimezone/blob/master/Classes/TimezoneMapper.swift

# Usage

On iOS, the code is available as a CocoaPod.  Just add this to your Podspec:

```
pod 'LatLongToTimezone'
```

Then in your code, you can do

```Swift
import LatLongToTimezone

let location = CLLocation(34, -122)
let timeZone: NSTimeZone = TimezoneMapper.latLngToTimezone(location)

```