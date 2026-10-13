# 하루여행 (OneDay Trip) — 안드로이드 하이브리드 앱

위치기반 여행 일정 및 맛집 지도 서비스 **하루여행 (oneday-trip)**을 탑재한 Kotlin 기반 안드로이드 하이브리드 웹뷰(WebView) 애플리케이션입니다.

---

## 📱 프로젝트 정보

- **패키지명 (Application ID)**: `kr.co.softone.onedaytrip`
- **앱 이름**: 하루여행 (OneDay Trip)
- **최소 SDK**: Android 7.0 (API 24)
- **타겟 SDK**: Android 16 (API 36)
- **웹 서비스 주소**: `https://oneday-trip.vercel.app` (로컬 개발 시 `http://10.0.2.2:5173` 또는 `http://localhost:5173`)

---

## 🌐 다국어 지원 (i18n)

기본 언어는 **한국어(Default)**이며, 기기 시스템 언어에 따라 4개 국어(5개 로케일)를 완벽 지원합니다.

| 로케일 | 지원 언어 | 앱 이름 | 주요 기능 문구 |
| :--- | :--- | :--- | :--- |
| `values/` (기본) | **한국어** | **하루여행** | "뒤로가기 버튼을 한 번 더 누르면 종료됩니다." |
| `values-ko/` | 한국어 | 하루여행 | 한국어 전용 리소스 |
| `values-en/` | 영어 (English) | OneDay Trip | "Press back again to exit." |
| `values-ja/` | 일본어 (日本語) | ワンデイトリップ | "もう一度戻るを押すと終了します。" |
| `values-zh-rCN/` | 중국어 간체 (简体中文) | 一日游 | "再按一次返回键退出应用。" |
| `values-zh-rTW/` | 중국어 번체 (繁體中文) | 一日遊 | "再按一次返回鍵結束應用程式。" |

---

## 🛠 주요 하이브리드 기능

1. **위치(GPS) 및 권한 연동**:
   - `WebChromeClient.onGeolocationPermissionsShowPrompt`를 통해 웹의 Geolocation API와 안드로이드 런타임 권한(`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) 완벽 연동
   - GPS 비활성화 시 설정 화면 이동 다이얼로그 제공
2. **외부 앱 스킴 (`nmap://`, `tel:`, `intent:`)**:
   - 네이버 지도 길찾기(`nmap://`) 호출 시 앱이 설치되어 있으면 앱으로 연결, 미설치 시 네이버 지도 웹/스토어로 안전하게 Fallback
   - 전화 걸기(`tel:`), 메일(`mailto:`) 인텐트 연결
3. **네이티브 브릿지 (`window.AndroidBridge`)**:
   - `GET_DEVICE_LANGUAGE`: 기기 시스템 언어(`ko`, `en`, `ja`, `zh-CN`, `zh-TW`)를 조회하여 웹 i18n과 동기화
   - `GET_APP_VERSION`: 앱 버전 조회
   - `SHOW_TOAST`: 네이티브 토스트 출력
   - `LOGOUT`: 웹 쿠키 세션 정리 및 로그아웃 처리
   - `downloadBlob`: 파일 다운로드 및 Notification 연동
4. **뒤로가기(Back) UX**:
   - 웹 브라우저 히스토리가 있으면 `webView.goBack()` 우선 실행
   - 최상위 페이지에서는 2초 내에 두 번 누를 때 안전하게 종료

---

## 💻 실행 및 디버깅

### 1. 개발 서버 로컬 연결 (adb reverse)
웹(`oneday-trip`) 개발 서버(`localhost:5173`)를 USB 연결된 폰에서 바로 보려면:
```bash
adb reverse tcp:5173 tcp:5173
```
이후 `MainActivity.kt`의 `DEFAULT_WEB_URL`을 `"http://localhost:5173"`으로 설정합니다.

### 2. Chrome 웹뷰 원격 디버깅
1. USB 디버깅이 켜진 폰을 Mac에 연결하고 앱을 실행합니다.
2. Mac 크롬 브라우저에서 `chrome://inspect/#devices` 접속
3. 연결된 기기의 `WebView in kr.co.softone.onedaytrip` 아래 **inspect** 클릭하여 개발자 도구 활용

### 3. 디버그 / 릴리스 빌드
```bash
# 디버그 설치
./gradlew installDebug

# 릴리스 번들(AAB - 구글 플레이스토어 제출용) 생성
./gradlew bundleRelease
# 결과물: app/build/outputs/bundle/release/app-release.aab
```

---

## 📚 스토어 배포 및 테스트 가이드

- **[스토어 등록 절차 및 애셋 가이드](등록과정/README.md)**
- **[Android & iOS 테스트 절차 가이드](테스트절차/README.md)** (구글 20명 비공개 테스트 및 iOS TestFlight 완벽 가이드)

