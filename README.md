# 챙김 CHAEGIM

가족 여행의 준비물 확인부터 차량 적재, 하차, 귀가 회수까지 이어주는 Android 앱입니다. 한 APK에서 스마트폰 화면과 Pleos Connect 차량 화면을 제공합니다.

**팀명: 짐 챙겼쓰?**

## 제출 APK

- [짐 챙겼쓰_챙김_release.apk](apk/짐%20챙겼쓰_챙김_release.apk)
- 저장소 내 경로: `apk/짐 챙겼쓰_챙김_release.apk`
- 앱 버전: **1.3 (versionCode 4)**
- 팀명의 물음표는 Windows 파일명 제한 때문에 APK 파일명에서만 제외했습니다.
- 제출용 release 키로 서명하며 `debuggable=false`입니다. 서명 키와 비밀번호는 저장소에 포함하지 않습니다.
- 기존 debug APK와 서명이 다르므로 덮어설치할 수 없습니다. 기존 데이터가 있는 앱을 삭제하기보다 별도의 테스트 기기에서 설치해 주세요. 이후 release 업데이트는 동일한 서명 키를 사용해야 합니다.

## 주요 기능

- 일정·목적지·출발 일시와 준비물·담당자·보관 위치 관리
- 모바일 준비 확인과 차량 적재 확인을 별도로 저장
- 하차·귀가 회수 확인, 최근 변경 되돌리기
- 활동·숙박일수·동행 인원 기반 준비물 추천과 선택 추가
- 초대 정보로 연결한 가족 기기 간 자동 동기화와 오프라인 변경 병합
- 미확인 준비물에 대한 출발 예약 알림
- 교육용 3D 모형 회전·확대, 관찰 미션과 메모
- Car UX 정책에 따른 차량 조작 제한
- Gleo Interface 추천 조회 호출 및 응답

추천은 로컬 규칙으로 계산합니다. Gleo 연동은 스키마 검토·승인과 실제 음성 환경에서의 검증이 별도로 필요합니다. 3D 모형은 실제 명소의 정밀 모델이 아닙니다.

## 실행 방법과 빌드

Android Studio의 JDK 17 이상과 Android SDK를 설치합니다. Android Studio에서 프로젝트를 열거나 `JAVA_HOME`과 `ANDROID_HOME`을 설정한 뒤 실행합니다. SDK 위치는 로컬 `local.properties`에 설정할 수도 있습니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
node --test sync-server/worker.test.mjs
```

macOS/Linux에서는 `./gradlew`를 사용합니다. APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 디버그 빌드는 시연용이며 배포용 서명은 별도로 구성해야 합니다.

제출용 release 빌드는 아래 환경 변수에 소유한 서명 키 정보를 설정한 뒤 생성합니다. 비밀번호와 키 파일은 저장소에 포함하지 않습니다. 서명 설정이 없으면 release 서명 검증에 실패합니다.

```powershell
$env:CHAEGIM_STORE_FILE='<keystore의 절대 경로>'
$env:CHAEGIM_STORE_PASSWORD='<keystore 비밀번호>'
$env:CHAEGIM_KEY_ALIAS='<키 별칭>'
$env:CHAEGIM_KEY_PASSWORD='<키 비밀번호>'
.\gradlew.bat :app:assembleRelease
```

기기에 APK를 설치하고 **챙김**을 실행합니다. 같은 APK가 폰에서는 모바일 화면, Android Automotive 기기에서는 차량 화면으로 열립니다. 모바일에서는 오늘 → 일정/준비물/귀가/설정, 차량에서는 준비물 확인/추천/3D 탐색/여행 기록/연결 설정으로 이동합니다.

## 테스트 환경 및 필요한 설정

| 항목 | 환경 |
| --- | --- |
| Pleos Connect 에뮬레이터 | **v2.1.0** |
| 운영체제 | Android Automotive OS, API 34 |
| 기본 디스플레이 | **2560 × 1440 px, 160 dpi, 가로형** |
| 차량 시스템 UI | Driving View·상태 표시줄·하단 GNB가 영역을 사용하며 앱은 가용 영역과 inset을 따릅니다 |
| 빌드 | compileSdk 36, targetSdk 34, minSdk 28, JDK 17 이상 |
| 검증 범위 | 차량 에뮬레이터와 모바일 UI 미리보기; 실제 스마트폰·실차 검증은 미완료 |

기본 일정·준비물과 교육용 3D 모형은 로그인이나 서버 설정 없이 사용할 수 있습니다. 가족 동기화는 인터넷과 동일한 초대 정보가 필요합니다. 출발 예약 알림은 실제 출발 일시와 알림 권한이 필요하며, 정확한 알림 권한이 없으면 Android가 전달을 지연할 수 있습니다. 한국어 설명에는 기기에 한국어 TTS 음성이 설치돼 있어야 합니다.

차량 화면은 실제 Car UX 정책이 허용할 때만 조작할 수 있습니다. release에는 디버그 제한 전환 기능이 없으며 정책 신호를 받지 못하면 조작을 잠급니다. Gleo 음성 연동은 별도 스키마 승인과 해당 서비스 환경이 필요합니다.

## 프로젝트 구성

| 경로 | 내용 |
| --- | --- |
| `app/src/main` | Android 앱과 Gleo 스키마 |
| `app/src/test` | 준비물·상태 전이·동기화 병합 단위 테스트 |
| `app/src/androidTest` | Android 호출·동기화·예약 알림 통합 검사 |
| `sync-server/worker.mjs` | Cloudflare Worker 동기화 API |
| `sync-server/schema.sql` | D1 데이터베이스 스키마 |
| `apk/` | 제출용 release APK |

## 동기화 서버 구성

앱은 초대 정보 `https://<서버주소>/trip#<64자리 무작위 비밀값>`으로 연결합니다. 서버에는 비밀값 원문 대신 SHA-256 해시를 `AUTH_SHA256` secret으로 설정하고 D1 데이터베이스를 `DB`로 바인딩합니다. `sync-server/schema.sql`을 데이터베이스에 적용한 뒤 Worker를 배포합니다. 초대 정보와 계정 인증 키는 저장소에 넣지 않습니다.

## 검증과 남은 범위

단위 테스트 34개와 서버 테스트 3개가 통과했습니다. Pleos 에뮬레이터에서 Android 추천 호출·응답, 추천 추가·되돌리기와 설정 화면을 확인했습니다. 실제 기기 조합, 실차 주행 중 정책 변경, Gleo 음성 인식과 한국어 TTS 청취 검증은 남아 있습니다.

여정 하나를 관리하는 구성입니다. 여러 여행의 기록 보관함, 실시간 날씨 추천, 실제 목적지 데이터와 정밀 3D 에셋 연동은 포함하지 않습니다. 모든 준비·적재·회수 표시는 사용자 확인 기록이며 센서 감지 결과가 아닙니다.
