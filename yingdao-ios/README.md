# YingDao iOS

一个基于 SwiftUI 的 iOS 相机原型，包含：
- 相机首页
- 实时引导浮层
- 拍后结果页
- 照片列表页

技术要点：
- `NavigationStack` 做类型安全导航
- `@Observable` 管理页面状态
- `AVFoundation` 做真实相机预览和拍照
- `Core/` 下的状态层可以用 `swift run YingDaoCoreVerification` 直接验证

## 当前状态

- `YingDaoIOS.xcodeproj` 已可用
- 模拟器构建已通过
- `com.yuki.yingdao.ios` 已可安装到 iOS 模拟器
- 相机页、实时引导浮层、拍后结果页、照片列表页都已接好

## 在模拟器运行

1. 打开 `YingDaoIOS.xcodeproj`
2. 选择一个 iPhone 模拟器
3. 直接点 `Run`

说明：
- 模拟器里可以验证页面流和状态流
- 模拟器不适合验证真实相机成像
- 如果模拟器没有可用相机源，预览可能是黑屏或占位画面，这是正常现象

## 在真机运行

1. 用 Xcode 打开 `YingDaoIOS.xcodeproj`
2. 选中 `YingDaoIOS` target
3. 进入 `Signing & Capabilities`
4. 在 `Team` 里选你的 Apple ID / 开发团队
5. 保持 `Signing` 为 `Automatically manage signing`
6. 连接 iPhone，信任这台 Mac
7. 在设备列表里选择你的 iPhone
8. 点击 `Run`

如果提示 bundle id 冲突：
- 把 `Bundle Identifier` 改成你自己的唯一值
- 例如：`com.<yourname>.yingdao.ios`

第一次上真机时你还可能需要：
- 在 iPhone 上进入 `设置 -> 通用 -> VPN 与设备管理`
- 信任你的开发者证书

真机测试建议优先验证：
- 相机权限弹窗
- 实时预览
- 拍照后跳转到拍后结果页
- 照片列表更新

## 重新生成工程

如果你改了工程生成脚本，可以重新执行：

1. 运行 `ruby scripts/generate_xcodeproj.rb`
2. 再用 Xcode 打开 `YingDaoIOS.xcodeproj`

## 运行核心验证

```bash
cd /Users/yuki/Documents/New\ project/yingdao-ios
swift run YingDaoCoreVerification
```

## 运行测试

```bash
cd /Users/yuki/Documents/New\ project/yingdao-ios
swift test
```
