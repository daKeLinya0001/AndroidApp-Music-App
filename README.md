# MusicApp · 本地音乐播放器

一个纯本地的 Android 音乐播放器，界面与交互移植自 DSH 桌面端「音乐播放器」插件。
使用 Kotlin + 传统 View 体系（无 Jetpack Compose），不联网、不上传任何数据。

> 版权：**@daKeLinya0001**

## 功能

- **音乐库**：通过系统文件选择器一次导入多个音频（mp3 / m4a / aac / wav / flac / ogg / opus / webm / aiff 等），文件复制到应用私有目录，卸载即清除
- **播放控制**：播放/暂停、上一首/下一首、拖动进度、三种播放顺序（列表循环 / 列表随机 / 单曲循环，同一个按钮循环切换，图标随模式变化）
- **后台播放**：播放时自动进入前台服务，退到后台或锁屏后继续播放、不会被系统回收；通知栏提供上一首 / 播放暂停 / 下一首控制与进度显示
- **频谱可视化**：基于 `Visualizer` 的真实 FFT 数据驱动 32 段频谱；未授予录音权限时自动退化为模拟律动
- **唱片动画**：黑胶旋转、呼吸光环、粒子与底部光晕（颜色可跟随频谱柱）
- **全屏播放器**：点击唱片展开，唱片周边是一圈首尾相连的能量曲线（含半透明填充与放射连接线），支持「弧线震动幅度」「弧线虚化程度」独立调节
- **主题**：浅色 / 深色 / 跟随系统；强调色与文字颜色可自定义，并按背景自动过滤不可见选项
- **壁纸**：图片或视频，带取景裁切；设置页背景完整同步当前壁纸（深色壁纸自动切换标题文字颜色）
- **同步歌词**：全屏播放时自动按曲名联网匹配歌词（网易云 → QQ 音乐 → 酷狗依次回退），
  按「歌名 + 时长」挑选最接近的版本，显示在进度条上方并随播放进度淡入滚动；
  设置里提供 ±5 秒「歌词延迟」用于手动对齐；结果缓存在本地，无歌词时显示「暂无歌词」
- **隐藏黑胶唱片**：开启后主界面与全屏都只保留文字、进度与频谱，卡片自动收缩为紧凑布局
- **壁纸**：上传图片或视频作为主界面与全屏播放器的背景，选完进入取景框拖动/缩放选择显示区域；也支持恢复默认纯色
- **外观微调**：频谱柱高度/速度/透明度/颜色、卡片透明度、卡片背后虚化、粒子大小、粒子数量（10–100）、光晕大小（0–150）
- **全屏粒子特效**：开关打开后主界面粒子也铺满整屏，与全屏播放器保持一致；此时频谱柱火花自动隐藏
- **设置管理**：所有修改即时保存、进程被杀不丢失；一键重置全部设置（不影响已导入的音乐）

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Android Studio | 支持 AGP 9.5 的版本（较新） |
| JDK | 17 及以上 |
| Android SDK | compileSdk / targetSdk 37 |
| Gradle | 9.6（wrapper 自动下载） |
| 最低运行版本 | Android 7.0（API 24） |

## 构建

```bash
# 调试包
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 发布包（未签名，需要自行签名后分发）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

签名可直接用 Android Studio：`Build → Generate Signed Bundle / APK`。
若要在 CI 或命令行签名，可在 `app/build.gradle.kts` 中补充 `signingConfigs` 并读取本地
`keystore.properties`（注意不要把它提交到仓库）。

> 注：AGP 9 的 `optimization { enable = true }` 会让 R8 对 kotlin 标准库做跨包合并，
> 运行时会抛 `IllegalAccessError`（应用启动即崩溃），因此默认未启用该优化；
> 如需更小的包体，可自行打开并完整回归测试。

## 权限说明

| 权限 | 用途 | 拒绝后 |
| --- | --- | --- |
| `RECORD_AUDIO` | 仅供 `Visualizer` 读取当前音频的 FFT 频谱 | 应用正常使用，频谱改为模拟律动 |
| `POST_NOTIFICATIONS`（Android 13+） | 显示播放控制通知 | 前台服务仍运行，但通知栏看不到控制条 |

应用**没有网络权限**，也不申请文件读写存储权限（导入走 SAF，用户选择后复制到私有目录）。

## 数据存放

| 内容 | 位置 |
| --- | --- |
| 音乐文件 | `files/tracks/`（应用私有目录） |
| 壁纸文件 | `files/wallpapers/` |
| 音乐库索引 | `SharedPreferences: music_library` |
| 全部设置 | `SharedPreferences: music_settings` |
| 播放状态 | `SharedPreferences: music_player` |

`android:allowBackup="false"`：卸载即彻底清除，重装不会被系统从备份恢复旧数据。

## 已知限制

- **锁屏 / 耳机线控未接入 `MediaSession`**：通知栏可以控制播放，但锁屏界面的媒体控制、蓝牙耳机按键暂时不可用
- **视频壁纸**静音循环播放，不支持声音；全屏与主界面共用同一段视频
- 界面按手机竖屏设计，横屏与平板未做专门适配
- 目标 SDK 为 37，克隆后需要较新的 SDK 与 Android Studio，否则改低 `compileSdk` / `targetSdk` 即可

## 目录结构

```
app/src/main/java/com/example/musicapp/
├── MainActivity.kt          主界面：播放控制、音乐库、全屏播放器、壁纸
├── SettingsActivity.kt      设置页
├── CropActivity.kt          壁纸取景框
├── MusicApp.kt              启动时应用主题
├── play/PlayerEngine.kt     MediaPlayer + Visualizer 单例引擎
├── play/PlayerSettings.kt   设置数据类与持久化
├── data/TrackStore.kt       导入、索引、音频元数据
├── ui/SpectrumView.kt       频谱 / 环形能量曲线
├── ui/ParticleView.kt       粒子与光晕
├── ui/DiscView.kt           黑胶唱片
├── ui/CropView.kt           壁纸取景手势与裁剪
└── ui/ThemeColors.kt        运行时强调色 / 文字颜色
```

## 许可证

[MIT](LICENSE) © daKeLinya0001
