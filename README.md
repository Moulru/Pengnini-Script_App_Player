# 펭니니 (Pengnini) · Script App Player

The Handy 디바이스와 동기화되는 funscript 재생을 지원하는 **Android 전용** 동영상 플레이어 + 라이브러리 앱.

---

## 📥 다운로드

최신 APK는 **[Releases 페이지](https://github.com/Moulru/Pengnini-Script_App_Player/releases/latest)** 에서 받을 수 있습니다.

**설치 방법**
1. 위 링크에서 `Pengnini-1.0.0v.apk` 다운로드
2. Android 설정 → 보안 → **"출처를 알 수 없는 앱" 설치 허용**
3. 다운로드한 APK 실행 → 설치

---

## 📦 소스코드 다운로드

오픈소스로 코드를 직접 받아서 빌드할 수 있습니다.

**방법 1 — Git clone**
```bash
git clone https://github.com/Moulru/Pengnini-Script_App_Player.git
```

**방법 2 — ZIP 다운로드**
이 페이지 상단의 **`<> Code`** 버튼 → **Download ZIP**

---

## ✨ 주요 기능

### 영상 플레이어
- AndroidX Media3 (ExoPlayer) 기반 재생 — `mp4` / `mkv` / `webm`
- 화면비 설정: Fit / Fill / Stretch / 16:9 / 4:3
- 1회용 재생속도 (0.5× ~ 2.0×)
- 가로 / 세로 화면 강제 회전 토글
- 핀치 줌으로 실제 영상 배율 변경 (50% ~ 400%)
- 좌측 영역 세로 스와이프 = 밝기 / 우측 영역 세로 스와이프 = 볼륨
- 더블탭으로 N초 seek (설정: 5/10/20/30초)
- 음소거 토글 + 볼륨 슬라이더
- 자막 자동 매칭 (`.srt`, `.ass`, `.vtt`) + CC on/off
- 이전 / 다음 영상 빠른 이동
- 영상 무한 루프

### Handy 동기화
- HSSP 프로토콜 (HandyFeeling Cloud 경유)
- funscript 자동 매칭 + 업로드
- HSTP 시간 동기로 정밀 재생
- **스크립트 오프셋** ±200ms 슬라이더 (퀵슬롯)
- **스트로크 범위** 0~100 듀얼 슬라이더 (`PUT /slide`)
- 영상 재생속도에 자동 동기화

### 라이브러리
- SAF (Storage Access Framework) 폴더 등록 — 다중 폴더 지원
- 그리드 / 리스트 뷰 토글
- 태그, 평점, 즐겨찾기
- 검색 + 필터 (스크립트 유무 / 폴더 / 태그 / 평점)
- 정렬 (추가일 / 제목 / 길이 / 평점 / 해상도 / 파일 크기)

### 보안 / 기타
- 앱 시작화면 **3×3 패턴 잠금** (선택)
- 백그라운드 재생 (Foreground Service)
- 다크 모드 고정
- 한국어 / English (시스템 언어 따라가기 기본)

---

## 🛠️ 소스에서 빌드

**필요 환경**
- Android SDK (API 35 / Build Tools 35)
- JDK 17
- Gradle 8.14+

**빌드 명령**
```bash
git clone https://github.com/Moulru/Pengnini-Script_App_Player.git
cd Pengnini-Script_App_Player

# 디버그 빌드
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 릴리스 빌드 (서명 키 필요)
./gradlew assembleRelease
```

`local.properties`에 `sdk.dir=...` 가 자동 생성되지 않으면 Android Studio에서 한 번 열어주세요.

---

## 📋 시스템 요구사항

- Android 8.0 (API 26) 이상
- Android 15 (API 35) 까지 호환 확인
- 인터넷 (Handy 동기화 시)
- 저장소 접근 권한 (SAF)

---

## 🔒 개인정보 / 데이터 처리

- Handy Connection Key는 `EncryptedSharedPreferences` 에 암호화 저장
- 잠금 패턴 역시 `EncryptedSharedPreferences` 에 암호화 저장
- 영상 / 스크립트 파일은 모두 **로컬 처리** (외부 전송 안 함)
- funscript 업로드 시 HandyFeeling 서버에 일시 호스팅됨 (Handy 디바이스 다운로드용)
- **시청 기록 / 통계 / 분석 정보는 저장·전송하지 않음**

---

## 🏗️ 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / UI | Kotlin + Jetpack Compose (Material 3) |
| 플레이어 | AndroidX Media3 (ExoPlayer) |
| 네트워크 | Retrofit + OkHttp + Kotlinx Serialization |
| 비동기 | Coroutines + Flow |
| DB | Room |
| 이미지 / 썸네일 | Coil (video frame decoder) |
| 파일 접근 | Storage Access Framework |
| 보안 | androidx.security (EncryptedSharedPreferences) |

---

## 🐛 이슈 / 제안

버그 제보 및 기능 제안은 [Issues 탭](https://github.com/Moulru/Pengnini-Script_App_Player/issues)을 이용해주세요.
