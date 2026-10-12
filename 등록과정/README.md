# 📋 하루여행 (OneDay Trip) 앱 스토어 등록 절차 안내

안드로이드(Google Play Store) 및 iOS(Apple App Store) 등록을 위한 전체 가이드 문서 모음입니다.

---

## 📑 목차 및 가이드 문서 바로가기

1. **[01. 구글 플레이스토어 등록 절차 & 체크리스트](01_구글_플레이스토어_등록_체크리스트_및_절차.md)**
   - 사전 준비물 (아이콘 512x512, 그래픽 1024x500, 스크린샷, 개인정보처리방침)
   - 앱 만들기 및 대시보드 10대 필수 설정 (위치 데이터 보안 포함)
   - 스토어 설명 작성 및 AAB 업로드
   - 개인 개발자 20명 비공개 테스트 정책 대응

2. **[02. 애플 앱스토어 등록 절차 가이드](02_애플_앱스토어_등록_절차_가이드.md)**
   - Apple Developer Program 및 App Store Connect 설정
   - Xcode 아카이브 및 빌드 업로드
   - 웹뷰 앱 심사 통과를 위한 핵심 주의사항 (Guideline 4.2 방지)

3. **[03. 서명키 관리 및 AAB 빌드 배포 방법](03_서명키_및_AAB_빌드_배포방법.md)**
   - `release.jks` 서명키 및 `local.properties` 관리
   - 구글 플레이 제출용 AAB 파일 생성 (`./gradlew bundleRelease`)
   - 버전 업그레이드 규칙 (`versionCode`, `versionName`)

---

## ⚡ 빠른 실행 명령어 요약

```bash
cd /Users/danyoh/XXX-GARDE/oneday-trip-app

# 1. 구글 플레이스토어 제출용 AAB 파일 생성
./gradlew bundleRelease

# 생성된 파일 위치:
# app/build/outputs/bundle/release/app-release.aab

# 2. 내 폰 직접 테스트용 릴리스 APK 생성
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```
