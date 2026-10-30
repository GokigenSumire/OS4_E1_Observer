# Hyper Wallpaper 兼容模块（LSPosed）

[Hyper Wallpaper Changer 主应用](https://github.com/GokigenSumire/OS3_Wallpaper_Changer)的可选桌面与锁屏兼容模块，独立安装、独立签名。主应用可以独立使用，只在设备需要输入兼容时安装本模块。

## 当前版本

模块 **97／1.1.22-gesture-continuity**，正式包名 `personal.wallpaper.os4.e1`；配套主应用 **94／1.2.6-p10-input-priority**。最低API31，targetSdk37，主要实测设备为小米17 Pro／Android16。

对应APK、冻结源码与校验文件见[主仓库Releases](https://github.com/GokigenSumire/OS3_Wallpaper_Changer/releases)。本仓库保存模块源码，两个工程分别管理。

## 功能范围

- 桌面与锁屏兼容分别开启，永久保存用户意愿，重启后恢复开启意愿并重新核验实际状态。
- 原生锁屏输入支持双击下一张、三击上一张、两指双击进入编辑。
- 图片忙时继续识别，通过当前场景内有界队列顺序处理，实际执行使用新鲜图片票据。
- 修复中继清空非READY场景票据，以及上一组超时双击误清下一组首按的问题。

## 安装与使用

1. 设备需要已有Root与LSPosed，先安装配套正式主应用和模块。
2. 在LSPosed为本模块保留“系统框架／Android”和“系统界面／SystemUI”作用域，正常重启加载。
3. 按需开启桌面或锁屏兼容；锁屏可选择“原生锁屏业务输入”和“锁屏手势最高优先级”。

初装兼容意愿默认关闭。最高优先级下，通知、媒体、顶部边缘等界面区域不作为手势避让区，系统动作可能同时响应；进入锁屏图片编辑仍须系统正常解锁。实际LOCK、来源与通道身份、图片票据和访问边界继续核验。

关闭最高优先级恢复默认局部保护，关闭LOCK兼容撤销旧识别与未发意图。必要时在LSPosed禁用模块并正常重启；保留主应用数据和来源授权。

## 最近验证记录（2026-10-28）

小米17 Pro用户已确认主94／模块97“测试基本符合预期”。模块正式构建1324项单测／80类通过，lint0错误29警告；联合门禁340项及45＋10项有效定向通过。

完整反例695/696匹配，一项预期原子状态门禁与实际拒绝项不一致，仍待修复复验。P10保持IN_PROGRESS；未逐项回传的用例不补记PASS，其他系统和长期边界以实际验证为准。

## 本地构建

使用JDK17、Android SDK37及仓库中的Gradle包装器，在本机`local.properties`配置SDK路径。调试构建命令：

```powershell
.\gradlew.bat :app:assembleDebug
```

正式构建的签名使用本机环境配置，密钥和密码不随仓库提供。自行签名的调试包使用独立身份，不能覆盖原正式签名包。

## 来源与许可

模块自有新代码采用[GPL-3.0](LICENSE)。Xposed API、Android Hidden API Bypass及其他来源见[第三方来源与许可](THIRD_PARTY_NOTICES.md)。系统参考材料不随仓库提供，原始诊断、签名材料、本机配置和构建产物不纳入版本控制。
