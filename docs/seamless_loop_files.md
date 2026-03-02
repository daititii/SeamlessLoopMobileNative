# 无缝循环（Seamless Loop）相关关键文件

本项目中与无缝循环（seamless loop）相关的关键文件，已按语言和功能分类如下：

## C++（Native 层）

| 文件路径 | 作用概述 |
|---|---|
| `app/src/main/cpp/AudioEngine.cpp` | 实现音频引擎核心，包含 `setLoopPoints(startFrame, endFrame)`、`setLooping(bool)` 等接口，用于设置循环区间和开启/关闭循环。 |
| `app/src/main/cpp/AudioEngine.h` | `AudioEngine.cpp` 的头文件，声明上述循环相关函数。 |
| `app/src/main/cpp/native-lib.cpp` | JNI 桥接层，暴露 `Java_com_cpu_seamlessloopmobile_jni_NativeAudio_setLoopPoints`、`setLooping`、`setLooping(isLooping)` 等本地方法，供 Kotlin 调用。 |
| `app/src/main/cpp/AudioDecoder.cpp` / `AudioDecoder.h` | 音频解码器，虽然主要负责解码，但在循环播放时会配合 `AudioEngine` 处理帧定位。 |
| `app/src/main/cpp/minimp3.h`、`minimp3_ex.h` | 第三方 MP3 解码库，提供底层帧数据，循环区间的精确定位依赖这些库。 |

## Kotlin（业务层）

| 文件路径 | 作用概述 |
|---|---|
| `app/src/main/java/com/cpu/seamlessloopmobile/jni/NativeAudio.kt` | 声明 `external fun setLoopPoints(start: Int, end: Int)`、`external fun setLooping(isLooping: Boolean)`，直接映射到 `native-lib.cpp`。 |
| `app/src/main/java/com/cpu/seamlessloopmobile/audio/Playback.kt` | `Playback` 接口实现，提供 `fun setLooping(looping: Boolean)`，在业务层统一调用 `NativeAudio.setLooping`。 |
| `app/src/main/java/com/cpu/seamlessloopmobile/audio/PlaybackManager.kt` | `PlaybackManager` 的核心实现，负责播放控制。单曲循环模式切换时调用 `NativeAudio.setLooping(isSingleLoop)`。 |
| `app/src/main/java/com/cpu/seamlessloopmobile/audio/MediaControlManager.kt` | UI 控制层，触发循环切换时会调用 `playbackService.playbackManager?.setLooping(isSingleLoop)`。 |
| `app/src/main/java/com/cpu/seamlessloopmobile/audio/PlaybackService.kt` | Service 层，持有 `PlaybackManager` 实例，间接提供循环功能给前端。 |
| `app/src/main/java/com/cpu/seamlessloopmobile/viewmodel/MainViewModel.kt` | 维护播放模式枚举（`LIST_LOOP`, `SINGLE_LOOP`），在切换到 `SINGLE_LOOP` 时会触发 `setLooping(true)`。 |

## 测试代码（验证循环逻辑）

| 文件路径 | 作用概述 |
|---|---|
| `app/src/test/java/com/cpu/seamlessloopmobile/data/ABLoopTest.kt` | 单元测试，验证 AB 循环点的设置与读取，间接涉及 `setLoopPoints`。 |
| `app/src/test/java/com/cpu/seamlessloopmobile/viewmodel/PlayModeTest.kt` | 测试播放模式切换（包括 `SINGLE_LOOP`），确保 UI 与底层 `setLooping` 调用保持一致。 |

## 关键调用链（简要概览）

1. **UI/ViewModel** -> `MainViewModel`（切换播放模式）  
2. **ViewModel** -> `MediaControlManager`（调用 `playbackManager?.setLooping(isSingleLoop)`)  
3. **PlaybackManager** -> `NativeAudio.setLooping(looping)`（Kotlin JNI）  
4. **NativeAudio.kt** -> `external fun setLooping(isLooping: Boolean)`  
5. **native-lib.cpp** -> `Java_com_cpu_seamlessloopmobile_jni_NativeAudio_setLooping`  
6. **AudioEngine.cpp** -> `audioEngine->setLooping(isLooping)`（实际音频引擎）
