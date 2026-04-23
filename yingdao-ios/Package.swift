// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "YingDaoCore",
    platforms: [
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "YingDaoCore",
            targets: ["YingDaoCore"],
        ),
        .executable(
            name: "YingDaoCoreVerification",
            targets: ["YingDaoCoreVerification"],
        ),
    ],
    targets: [
        .target(
            name: "YingDaoCore",
            path: "Core",
        ),
        .executableTarget(
            name: "YingDaoCoreVerification",
            dependencies: ["YingDaoCore"],
            path: "Verification",
        ),
        .testTarget(
            name: "YingDaoCoreTests",
            dependencies: ["YingDaoCore"],
            path: "Tests/YingDaoCoreTests",
        ),
    ],
)
