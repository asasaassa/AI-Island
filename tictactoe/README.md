# Tic-Tac-Toe with TensorFlow Lite

Android에서 TensorFlow Lite 모델을 사용하여 틱택토 게임의 AI 추천 기능을 구현한 프로젝트입니다.

## 주요 기능

- 3x3 틱택토 게임 (X와 O가 번갈아 플레이)
- TensorFlow Lite 모델을 사용한 AI 추천
- **AI 추천 시각화**: 빈 칸의 배경색이 어두울수록 AI가 추천하는 좋은 수
- Jetpack Compose UI

## 기술 스택

- **Kotlin** + **Jetpack Compose**
- **TensorFlow Lite 2.16.1** (ML 모델 추론)
- **AndroidViewModel** (상태 관리)
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 36 (Android 15+)

## 프로젝트 구조

```
app/src/main/
├── assets/
│   └── tictactoe.tflite          # TensorFlow Lite 모델 파일
├── java/com/study/tictactoe/
│   ├── MainActivity.kt            # 메인 액티비티
│   ├── TicTacToeModel.kt         # TFLite 모델 래퍼
│   ├── TicTacToeViewModel.kt     # 게임 로직 & 상태 관리
│   └── TicTacToeScreen.kt        # Compose UI
```

## ML 모델 정보

### 모델 입력/출력
- **입력**: 3x3 float 배열
  - 현재 플레이어의 말: `1.0`
  - 상대방의 말: `-1.0`
  - 빈 칸: `0.0`
- **출력**: 3x3 float 배열 (각 위치의 점수, 높을수록 좋은 수)

### 모델 학습
- 랜덤 플레이어 2명이 100만 게임을 플레이한 데이터로 학습
- 완벽한 플레이어는 아니지만 합리적인 수를 추천

## 빌드 및 실행

```bash
./gradlew assembleDebug
```

APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

## 16KB 페이지 크기 호환성 문제 및 해결

### 문제 상황

Android 15+ (API 35+)에서 다음 에러가 발생할 수 있습니다:

```
이 앱은 16KB 와 호환되지 않습니다. ELF 정렬 검사에 실패했습니다.
lib/arm64-v8a/libtensorflowlite_jni.so
lib/arm64-v8a/libandroidx.graphics.path.so
```

### 원인

Android 15부터 일부 기기(특히 arm64-v8a)가 메모리 페이지 크기를 **4KB → 16KB**로 변경했습니다.

네이티브 라이브러리(`.so` 파일)가 16KB 경계에 맞춰 정렬(alignment)되어야 하는데, 다음 라이브러리들이 아직 16KB 정렬을 지원하지 않습니다:

1. **`libtensorflowlite_jni.so`** (3.2MB)
   - TensorFlow Lite의 네이티브 라이브러리
   - TFLite 모델 추론을 실행하는 C++ 코드

2. **`libandroidx.graphics.path.so`** (9.9KB)
   - Jetpack Compose의 그래픽 렌더링 라이브러리

### 해결 방법

#### 1. AndroidManifest.xml 설정

`<application>` 태그 안에 다음 속성을 추가하여 4KB 모드로 실행:

```xml
<property
    android:name="android.app.PROPERTY_COMPAT_ALLOW_NATIVE_HEAP_POINTER_TAGGING"
    android:value="false" />
```

**효과**: 앱이 16KB 기기에서도 4KB 페이지 크기 모드로 실행되어 호환성 유지

#### 2. build.gradle.kts 설정

NDK ABI 필터를 명시적으로 지정:

```kotlin
defaultConfig {
    ndk {
        abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }
}
```

#### 3. TensorFlow Lite 버전 업데이트

```kotlin
implementation("org.tensorflow:tensorflow-lite:2.16.1")
```

최신 버전일수록 16KB 지원이 개선되지만, 2.16.1도 아직 완전히 지원하지 않아 위의 호환성 설정이 필요합니다.

### 성능 영향

- 4KB 모드로 실행되므로 성능이 약간 낮을 수 있지만, 대부분의 경우 눈에 띄지 않습니다
- 틱택토처럼 가벼운 ML 모델에는 영향이 거의 없습니다

### 향후 전망

TensorFlow Lite 2.17+ 버전에서 16KB 페이지 크기를 완전히 지원할 것으로 예상됩니다. 그때 AndroidManifest의 호환성 속성을 제거할 수 있습니다.

## APK 구조

```
app-debug.apk
├── assets/
│   └── tictactoe.tflite          # ML 모델 (23KB)
└── lib/
    ├── arm64-v8a/
    │   ├── libandroidx.graphics.path.so      # Compose (10KB)
    │   └── libtensorflowlite_jni.so         # TFLite (3.2MB)
    ├── armeabi-v7a/
    ├── x86/
    └── x86_64/
```

## 참고 자료

- [Firebase ML Kit Custom Models for iOS (Medium)](https://medium.com/firebasethailand/firebase-ml-kit-custom-models-for-ios-developers-part-2-implementing-tic-tac-toe-44f1cd9f99c5)
- [Android 16KB Page Size Documentation](https://developer.android.com/guide/practices/page-sizes)
- [TensorFlow Lite Android Guide](https://www.tensorflow.org/lite/android)

## 라이선스

MIT License
