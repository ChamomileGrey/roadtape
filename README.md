# RoadTape (로드테이프)

창문을 닫아도 바깥의 바람·빗소리·도로의 결을 주행 데이터로 되살려 들려주는 차량용 사운드 앱.

현대자동차그룹 차량용 앱 개발 공모전 출품작의 프로토타입 (v0.1.0).

## 지금 동작하는 것

- **오픈윈도 모드**: 켜면 바깥 소리 레이어가 재생됨. 강도 3단계(은은하게 / 창문 반쯤 / 창문 활짝).
- **속도 연동**: 속도가 오르면 바람 소리가 커지고 밝아지며, 노면 소리가 깊어짐. 정차 시에는 거의 무음.
- **와이퍼 단계 → 빗소리**: 단계가 높을수록 빗방울이 촘촘해지고, 속도가 빠를수록 더 세게 부딪힘.
- **실제 창문 열림 → 자동 감소**: 창문을 열수록 앱 소리를 줄여 실제 바깥 소리와 교대.
- **터널 구간**: 울림(리버브)이 더해지고 빠져나오면 잔향이 자연스럽게 사라짐.
- **오디오 포커스**: 내비게이션 안내 등 다른 소리가 나오면 자동으로 줄이거나 멈춤. 출력 상한 고정.

모든 소리는 녹음 파일 없이 노이즈 합성과 필터로 실시간 생성함 (외부 음원·라이브러리 없음).

## 신호 연결 상태

| 입력 | 현재 | 계획 |
|---|---|---|
| 속도, 기어 | AAOS 표준 `CarPropertyManager` 코드 포함 (자동차 기기에서 자동 연결), 그 외에는 화면의 시연용 패널 | 유지 |
| 와이퍼, 창문 | 시연용 패널로 입력 | Pleos Connect Vehicle SDK (Wiper / Window API) |
| 터널·해안 구간 | 시연용 패널로 입력 | FusedLocation / NaviHelper SDK |
| 음성 명령·안내 | 없음 | SpeechToText / TextToSpeech SDK |

## 실행 방법

1. Android Studio 최신 버전으로 이 저장소를 열기
2. Pleos Connect 에뮬레이터(AVD) 실행
3. `app` 실행, 또는 APK 직접 설치:
   ```
   adb install apk/ChamomileGrey_RoadTape_release.apk
   ```
4. **오픈윈도 켜기**를 누르고, 오른쪽 **시연용 입력 패널**에서 속도·와이퍼·창문·터널을 조작

명령줄 빌드:

```
./gradlew testReleaseUnitTest assembleRelease
```

## 테스트 환경과 설정

- 빌드: Gradle 8.14.3, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, JDK 21
- `compileSdk` 34, `targetSdk` 34, `minSdk` 28
- 단위 테스트 9개(사운드 엔진: 속도·와이퍼·창문·터널·강도·포커스·출력 상한) 통과
- 권한: `android.car.permission.CAR_SPEED`(첫 실행 시 허용 필요), `android.car.permission.CAR_POWERTRAIN`
- 네트워크·계정·추가 설정 불필요

### Pleos Connect 에뮬레이터

- 대상 버전: Pleos Connect Emulator v2.1.0 (Android API 34)
- 화면 규격: [에뮬레이터 실행 확인 후 기입]
- 에뮬레이터 실행 확인: **아직 하지 않음.** 현재까지는 빌드와 단위 테스트만 확인된 상태.

## APK

- 위치: [`apk/ChamomileGrey_RoadTape_release.apk`](apk/ChamomileGrey_RoadTape_release.apk)
- release 빌드이며 프로토타입용 디버그 키로 서명됨.

## 구조

```
app/src/main/java/com/chamomilegrey/roadtape/
  MainActivity.kt                  화면 (오픈윈도 스위치, 강도, 시연용 패널)
  audio/AudioEngine.kt             오디오 스레드, 오디오 포커스
  dsp/SoundscapeSynth.kt           바람·노면·비·터널 합성
  dsp/Filters.kt                   노이즈, 필터, 콤 딜레이
  vehicle/VehicleSignalSource.kt   차량 신호 (AAOS 속도·기어)
app/src/test/                      사운드 엔진 단위 테스트
```

## 다음 단계

- Pleos Connect Vehicle SDK 연동 (와이퍼, 창문, 방향지시등, 주행 모드, 경고)
- 위치 기반 구간 레이어 (해안, 숲길, 교량)
- 사운드 수집 도감과 드라이브 테이프 자동 생성
- 음성 명령, 백그라운드 재생용 미디어 서비스
