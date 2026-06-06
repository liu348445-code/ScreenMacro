# 屏幕宏录制器 - 构建指南

## 项目简介

一个 Android 应用，可以：
1. **录制**：录制屏幕操作（触摸点击、滑动等）
2. **回放**：自动回放录制的操作
3. **变速**：0.25x ~ 5x 倍速回放

## 环境要求

- **Android Studio** Hedgehog (2023.1.1) 或更新版本
- **JDK** 17
- **Android SDK** API 34
- **Gradle** 8.5 (项目自带 wrapper)

## 构建步骤

### 1. 用 Android Studio 打开项目

打开 Android Studio → `File` → `Open` → 选择 `ScreenMacro` 文件夹

### 2. 等待 Gradle 同步

Android Studio 会自动下载依赖。如果遇到网络问题：
- 确保能访问 `services.gradle.org` 和 `google()`
- 或在 `build.gradle.kts` 中配置国内镜像

### 3. 直接构建 APK

菜单栏 → `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

或者用命令行（在项目目录下）：
```bash
./gradlew assembleDebug
```

APK 生成路径：`app/build/outputs/apk/debug/app-debug.apk`

### 4. 安装到手机

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

或者直接把 APK 传到手机上安装。

## 使用步骤

### 首次使用 - 权限设置

1. 打开 APP → 按提示授予「悬浮窗权限」
2. 打开「无障碍服务」→ 找到「屏幕宏录制器」→ 开启
3. （Android 13+）授予通知权限
4. 点击「启动控制面板」

### 录制操作

1. 点击浮动小球 → 展开控制面板
2. 点「🎬 开始录制」→ 开始操作手机
3. 操作完成后点「🔴 停止录制」

### 回放操作

1. 控制面板中选择速度（0.25x ~ 5x）
2. 点击「▶ 播放录制」
3. 应用会自动回放你的操作

## 注意事项

- ⚠️ 录制时触摸会被覆盖层捕获，不会穿透到下层应用
- ⚠️ 建议录制简单操作（如登录流程、点击序列）
- ⚠️ 复杂手势（拖拽、多点触控）可能不太准确
- 回放时建议先用手动模式测试

## 项目结构

```
ScreenMacro/
├── app/src/main/java/com/screenmacro/app/
│   ├── MainActivity.kt          # 主界面 + 权限引导
│   ├── FloatingControlService.kt # 悬浮控制面板
│   ├── TouchRecorder.kt          # 触摸事件录制器
│   ├── ScreenRecordService.kt    # 屏幕视频录制
│   ├── MacroAccessibilityService.kt # 无障碍回放服务
│   ├── PlaybackOverlayService.kt # 视频回放覆盖层
│   └── MacroData.kt             # 数据模型
└── app/src/main/res/            # 资源文件
```
