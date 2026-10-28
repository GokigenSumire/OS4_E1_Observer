# 第三方来源与许可

独立兼容模块自身新代码采用GPL-3.0，完整许可证见LICENSE。没有复制主壁纸Shimmer的实现、系统桌面、framework或services的实现，只按用户提供的同构建材料核对反射签名与读字段。此包仅个人自用，“正式release”指构建配置，不是公开发布、OEM认证或解决主壁纸工程许可混用问题。

Xposed legacy API 82：编译期接口 `de.robv.android.xposed`，来源 https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar ，SHA-256 `F48C635F1C7469FDEC0E00AD2EA0B7A6B2F5B55065784A35B7CA3A84615E8E25`。作者 rovo89 / XposedBridge；上游仓库 https://github.com/rovo89/XposedBridge ，Apache-2.0。API JAR 为 compileOnly，不进入候选 DEX；未声称这是手机 LSPosed 分支的签名／兼容认证。

Gradle Wrapper 从本工作区既有 Gradle9.7.1 原样复制（Apache-2.0）；Android 构建插件 AGP9.4.1 使用既有工具依赖，不自动安装 SDK 或 JDK。JUnit4.13.2（EPL-1.0）只用于电脑单元测试，不进入 APK。Android SDK 平台遵循其原始使用条款。

Android Hidden API Bypass 6.1：`org.lsposed.hiddenapibypass:hiddenapibypass:6.1`，LSPosed 项目，Apache-2.0。真实Maven Central POM已于2026-10-17取得HTTP200，声明此版本与许可，来源 https://github.com/LSPosed/AndroidHiddenApiBypass 。仅模块自己的中继进程取系统现有AMS Binder时，限定豁免 `Landroid/app/ActivityManager;`；不扩大到所有隐藏API、不绕系统安全解锁或修改SELinux／系统文件。实际AAR／依赖许可与哈希在本轮构建后另核；POM身份不冒手机兼容已通过。

Robolectric 4.14.1：`org.robolectric:robolectric:4.14.1`，用于模块70真实Android View／Drawable／Animator适配单元测试。本机实际缓存POM声明MIT，来源 https://github.com/robolectric/robolectric 。仅testImplementation及宿主测试依赖，不作为模块运行库进入APK；不把其宿主结果冒真实SystemUI构造、LSPosed输入或目标机通过。测试中的同名简化View是本项目自写夹具，不复制或打包OEM实现。
