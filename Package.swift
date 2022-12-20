// swift-tools-version:5.0
import PackageDescription

let dependencies: [Target.Dependency] = [
]

let package = Package(
    name: "LatLongToTimezone",
    products: [
      .library(name: "LatLongToTimezone", targets: ["LatLongToTimezone"])
    ],
    targets: [
        .target(
            name: "LatLongToTimezone",
            dependencies: dependencies,
            path: "Classes",
            exclude: [
            ]
        ),
    ]
)