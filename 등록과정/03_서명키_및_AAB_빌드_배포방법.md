# 03. 서명키(Keystore) 관리 및 AAB 빌드 배포 가이드

이 문서는 **하루여행 안드로이드 앱**의 서명키 관리 및 구글 플레이스토어 배포용 AAB(Android App Bundle) 파일을 빌드하는 방법을 설명합니다.

---

## 🔑 1. 서명키(Keystore) 관리

### 현재 설정 요약
- **키 파일**: `oneday-trip-app/release.jks`
- **키 별칭(Alias)**: `key0`
- **비밀번호 설정 파일**: `oneday-trip-app/local.properties`
  ```properties
  RELEASE_STORE_PASSWORD=your_store_password
  RELEASE_KEY_PASSWORD=your_key_password
  ```

> ⚠️ **보안 주의사항**:
> `release.jks` 파일과 `local.properties`는 외부(Git 공개 저장소 등)에 노출되지 않도록 각별히 유의해야 하며, 절대 분실하지 않도록 별도의 안전한 저장소(USB, 클라우드 백업 등)에 보관하세요. (키 분실 시 구글 플레이스토어 앱 업데이트가 불가능해질 수 있습니다.)

---

### 하루여행 전용 신규 키스토어를 생성하고자 할 때
만약 기존 `release.jks` 대신 완전히 새로운 키스토어를 발급받으려면 터미널에서 아래 명령을 실행합니다:

```bash
cd /Users/danyoh/XXX-GARDE/oneday-trip-app

keytool -genkey -v \
  -keystore release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias onedaytrip
```

입력 프롬프트에서 비밀번호와 이름/조직 정보를 입력한 뒤 생성된 정보를 `local.properties`와 `app/build.gradle.kts`에 맞춰주시면 됩니다.

---

## 📦 2. 구글 플레이 제출용 AAB 빌드

구글 플레이스토어는 APK가 아닌 **AAB(Android App Bundle)** 형식만 업로드를 허용합니다.

### 빌드 명령어

```bash
cd /Users/danyoh/XXX-GARDE/oneday-trip-app

# 릴리스 번들(AAB) 빌드
./gradlew bundleRelease
```

### 빌드 결과물 생성 경로
빌드가 완료되면 다음 위치에 업로드용 파일이 생성됩니다:
```text
oneday-trip-app/app/build/outputs/bundle/release/app-release.aab
```
👉 이 `app-release.aab` 파일을 Google Play Console의 **[출시] ➔ [새 버전 만들기]** 화면에 드래그하여 업로드하시면 됩니다.

---

## 📱 3. 실제 기기 직접 테스트용 APK 빌드 (선택)

구글 플레이 업로드 전 본인 스마트폰에서 직접 설치해보고 싶다면 APK를 빌드합니다:

```bash
# 릴리스 APK 빌드
./gradlew assembleRelease

# 빌드 결과물 위치
# oneday-trip-app/app/build/outputs/apk/release/app-release.apk

# USB 연결된 폰에 바로 설치
./gradlew installRelease
# 또는
adb install app/build/outputs/apk/release/app-release.apk
```

---

## ⚙️ 4. 버전 업데이트 방법 (앱 업데이트 시)

추후 앱을 수정하고 스토어에 새 버전을 올릴 때는 `app/build.gradle.kts`에서 버전을 올려야 구글 플레이 콘솔에서 업로드가 허용됩니다:

```kotlin
// oneday-trip-app/app/build.gradle.kts
defaultConfig {
    versionCode = 2          // 매 배포마다 1씩 증가 (정수)
    versionName = "1.0.1"    // 사용자에게 보이는 버전 문자열
}
```
버전을 올린 후 다시 `./gradlew bundleRelease`를 실행하여 새 AAB를 콘솔에 업로드하면 됩니다.
