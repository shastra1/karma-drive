# KARMA DRIVE — 운전습관 디지털 트윈카 (프로토타입 v0.3)

> **운전자는 운전만 한다.** 차량 신호(Pleos Vehicle SDK) · 가속 상태 · 공공 도로 데이터(어린이보호구역 · 제한속도 · 돌발정보)로
> 지킨 것과 어긴 것을 자동으로 기록하고, 그 결과가 **디지털 트윈카**에 바로 새겨집니다.
> 위반할 때마다 트윈카의 해당 부위가 찌그러지고 · 긁히고 · 유리에 금이 가고 · 연기가 나며, **왜 망가졌는지 사유**가 상처마다 남습니다.
> 운전자는 주행 중 화면을 볼 수 없지만, **동승자 · 가족은 KARMA 앱(라이브 트윈)으로 실시간 관람**하고 응원을 보냅니다.
> 주차(P)하면 운전자에게 리플레이 · 언박싱 · 상처 리포트가 열리고, KP로 정비소에서 고칩니다.
>
> 2026 현대자동차그룹 차량용 앱 공모전 "Code your App into Pleos App Market" 출품작.

## APK (바로 설치)
| 파일 | 내용 | 설치 대상 |
|---|---|---|
| [`apk/고릴라스랩_KARMADRIVE_release.apk`](apk/) | KARMA 인카 앱 (판정 · 트윈카 · 라이브 공유 서버) | Pleos Connect 에뮬레이터 (API 34) |
| [`apk/고릴라스랩_KARMADRIVE-mobile_release.apk`](apk/) | KARMA 스마트폰 앱 (라이브 트윈 관람) | Android 9 이상 폰 · 에뮬레이터 |

- 설치: APK 파일을 에뮬레이터 화면에 끌어다 놓거나 `adb install <파일>`.
- 테스트 방법: 인카 앱 허브의 **데모 주행 4종** → 라이브 트윈(`adb forward tcp:8765 tcp:8765` → http://127.0.0.1:8765) → 기어 P → 리플레이 · 언박싱 → 정비소.
- 실제 차량 신호: Pleos Connect **Virtual Console**(`adb forward tcp:8088 tcp:8088`)에서 기어 D · ACCEL · 조향 · 방향지시등 조작 → 인카 앱이 Pleos Vehicle SDK로 받아 판정.

## 처음 받은 사람 — 5단계로 실행하기
1. **Android Studio**(Ladybug 이상, Gradle JDK 21)와 **Pleos Connect 에뮬레이터**(Pleos Playground 가이드)를 설치합니다.
2. 이 저장소를 받습니다: Android Studio 시작 화면 → **Clone Repository** → 저장소 주소 붙여넣기.
3. 프로젝트를 열면 Gradle Sync가 자동으로 돌고 `local.properties`가 만들어집니다.
4. (선택) 실제 도로 매칭까지 보려면 `local.properties.example`을 참고해 **본인 키**를 `local.properties`에 추가합니다.
   - data.go.kr 회원가입 → `전국무인교통단속카메라표준데이터` · `전국어린이보호구역표준데이터` 활용 신청 → 일반 인증키(Decoding) → `DATA_GO_KR_KEY=...`
   - 키가 없어도 빌드 · 데모 주행 · 판정 · 트윈카 · 라이브 공유 · Pleos 차량 신호는 모두 동작합니다(도로 매칭만 비어 있음).
5. 실행 대상 **incar-app**, 기기 **Pleos Connect** 선택 → 초록 ▷ 실행.
   - Virtual Console: 터미널에서 `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088
   - 라이브 트윈: `adb forward tcp:8765 tcp:8765` → http://127.0.0.1:8765

> 키 · 비밀값(`local.properties`, `pleos_keys.txt`)은 절대 커밋하지 않습니다(.gitignore 처리됨).

## 한눈에 보는 구조
```
[차량 신호]  Pleos Vehicle SDK: 방향지시등 · 조향각 · 안전벨트 · 와이퍼 · 브레이크 · 전조등
             AAOS Car API: 기어 · 차속         GPS: 위치
[도로 데이터] 어린이보호구역(data.go.kr) · 무인단속카메라 제한속도(data.go.kr) · 돌발정보(ITS)
      │
      ▼
[카르마 엔진] 매너 스킬 13종(준수 → +KP) · 흑역사 10종(위반 → −KP · 데미지)
      │  흑역사가 판정되는 그 순간
      ▼
[트윈카]  부위별 상처 + 사유  ──▶  라이브 공유(동승자 · 가족 폰 · PC)   ※ 운전자 화면은 주행 중 0
      │  주차(P)
      ▼
[운전자]  리플레이 → 언박싱(응원 도착) → 흑역사 리포트(상처 · 사유) → 정비소(KP로 부위 수리)
```

## 모듈
| 모듈 | 내용 |
|---|---|
| `incar-app` | KARMA 인카 앱 (Pleos Connect, Kotlin · Compose). 판정 · 트윈카 · 라이브 공유 서버 내장 |
| `mobile-app` | KARMA 앱(스마트폰) 프로토타입 — 라이브 트윈 · 상처 사유 · 정비소 · 응원. 화면은 `assets/app/index.html` |

## 빌드 · 실행
1. Android Studio → **Open** → 이 폴더(`karma-drive`) → Gradle Sync
   - Pleos Vehicle SDK는 `nexus-playground.pleos.ai` 저장소에서 받습니다. 받지 못하면 `incar-app/build.gradle.kts`의 `implementation("ai.pleos.playground:Vehicle:2.3.2")` 한 줄만 지우면 SDK 없이도 빌드됩니다.
2. Pleos Connect Emulator(API 34, Cold boot) 실행
3. 실행 대상 `incar-app` → **▶ Run** (저장 형식이 바뀌어 앱이 바로 꺼지면 `adb uninstall com.karmadrive.incar` 후 다시 실행)

## 시연 — 트윈카가 망가지는 걸 다른 사람이 실시간으로 보기
1. 인카 앱 허브 → `▶ 트윈카 수난 시대 (라이브 공유)` (3배속 · 약 40초)
2. PC에서 명령 프롬프트:
   ```
   C:\Android\sdk\platform-tools\adb forward tcp:8765 tcp:8765
   ```
3. PC 브라우저로 **http://127.0.0.1:8765** → 라이브 트윈 화면 (동승자 · 가족 화면)
   - 스쿨존 침공 → 보닛 찌그러짐 + 연기, 우회전 패싱 → 펜더 찌그러짐, 레드카드 → 헤드램프 파손,
     과속 스캔들(대) → 앞 유리 금, 깜빡이 실종 → 도어 긁힘 — 생길 때마다 "쾅!" + 사유
   - 핀을 누르면 사유 · 판정 근거(어떤 공공데이터 · 어떤 차량 신호)를 보여 줍니다
   - 응원 스티커를 보내면 운전자는 주차 후 언박싱에서 받습니다
4. 차량 화면은 주행 내내 검은 화면(주행 제한) → 주차하면 언박싱 · 흑역사 리포트 · 정비소
5. (선택) 폰 에뮬레이터에 `mobile-app` 실행 → ⚙ 서버 주소 `http://10.0.2.2:8765`
   - 에뮬레이터가 두 대일 때는 `adb -s emulator-5554 forward tcp:8765 tcp:8765`처럼 차량 에뮬레이터를 지정
   - 차와 연결되지 않으면 KARMA 앱은 데모 데이터로 보여 줍니다 (`index.html?demo=1`)

그 밖의 데모 주행: `출근길 S등급` · `흑역사 주행` · `법규 데모 (도심)` (8배속).
실제 신호: Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088)에서 기어 D → 조작 → P.
`신호 점검` 화면에서 Pleos SDK 신호(방향지시등 · 조향각 · 안전벨트 · 와이퍼 · 브레이크 · 전조등)의 수신 여부와 값을 확인합니다.

## 판정 규칙 v2 — 지키면 +KP, 어기면 −KP · 데미지 · 트윈카 상처
### 매너 스킬 (준수)
| 이름 | 조건 | 근거 데이터 | KP |
|---|---|---|---|
| 퍼펙트 스톱 | 우측 깜빡이 + 3km/h 이하 1초 정지 → 우회전 | 방향지시등 · 차속 · 조향 | +5 |
| 깜빡이 콤보 | 방향 전환 때 깜빡이 ON(끈 지 3초 이내 포함), 연속 성공 | 방향지시등 · 조향 | +2 × 콤보 배율(최대 2배) |
| 옐로 브레이크 | 황색불에 정지선 앞 15m 이내 정차 | 신호 · 차속 | +5 |
| 스쿨존 스텔스 | 어린이보호구역 통과 중 최고 30km/h 이하 | **어린이보호구역 표준데이터** · 차속 | +10 |
| 리미트 키퍼 | 1km 구간 제한속도 +5km/h 이내 | **무인단속카메라 제한속도** · 차속 | +2 / km |
| 정지선 마스터 | 적색 신호에 정지선 앞 15m 이내 정차 | 신호 · 차속 | +3 |
| 돌발 구간 세이프 패스 | 사고 · 공사 구간을 급조작 없이 제한속도 이하로 통과 | **ITS 돌발정보** · 차속 | +3 |
| 버터 드라이빙 | 1km 동안 가속도 0.25g 미만 | 차속(가속도) | +2 / km |
| 레인 마스터 | 와이퍼 ON 구간 버터 드라이빙 | 와이퍼 | ×1.5 |
| 에코 홀드 | 정차 30초~3분 뒤 부드럽게 출발 (RUN당 5회) | 기어 · 차속 | +3 |
| 올 벨트 스타트 | 기어 D 때 안전벨트 착용 | 안전벨트 · 기어 | +3 |
| 골든 로드 | 오늘의 숨은 보너스 지점 통과 (주차 후 공개) | 주행 거리 | +30 |
| 주행 거리 보상 | 거리 × 매너 지수 배율 (MQ 90↑ 1.5배, 70 미만 0.5배) | 주행 거리 | km × 배율 |

### 흑역사 (위반) → 트윈카 상처
| 이름 | 조건 | KP | 데미지 | 트윈카 부위 · 상처 |
|---|---|---|---|---|
| 분노의 질주 | 급가속 0.40g↑ / 급정거 0.45g↑ (경미) | −5 | +2 | 앞 바퀴 · 앞 범퍼 먼지 · 잔 스크래치 |
| 공회전 명상 | 정차 중 10분 초과 (경미) | −10 | +2 | 배기구 그을음 |
| 정지선 침범 | 적색 신호에 정지선을 넘어 정차 (경미) | −5 | +2 | 앞 범퍼 긁힘 |
| 깜빡이 실종 사건 | 깜빡이 없이 방향 전환 | −10 | +3 | 운전석 도어 긁힘 |
| 노벨트 챌린지 | 벨트 미착용으로 10km/h 넘게 10초 | −20 | +5 | 실내 경고등 |
| 과속 스캔들(소) | 제한속도 +10~20km/h 5초 | −20 | +5 | 앞 범퍼 찌그러짐 |
| 우회전 패싱 | 횡단보도 앞 우회전인데 일시정지 없이 10km/h 이상 회전 | −30 | +8 | 앞 펜더 찌그러짐 |
| 과속 스캔들(대) | 제한속도 +20km/h 초과 5초 | −50 | +10 | 앞 유리 금 |
| 레드카드 | 적색 신호에 정지선 통과 | −100 | +20 | 헤드램프 파손 |
| 스쿨존 침공 | 어린이보호구역 30km/h 초과 | −100 | +20 | 보닛 찌그러짐 + 연기 |

- 하루 첫 "경미" 흑역사는 경고만 · RUN당 데미지 상한 25 (상한 뒤 위반은 상처 · 사유만 남음)
- 매너 지수 MQ = 100 − 데미지 × 3 · 등급 S ≥ 25KP · A ≥ 12 · B ≥ 4 · C ≥ 0 · D < 0
- 데미지 6단계(트윈카 그림): 0 쇼룸 → 1~20 먼지 → 21~40 도어 긁힘 → 41~60 범퍼 찍힘 → 61~80 유리 금 · 휠 휨 → 81~100 연기 · 램프 파손
- 수리: 부위별 데미지 1당 120 KP(최소 100) · 풀 리페어 20% 할인 · 세차 미니게임 · 3일 무흑역사 자연 회복 −5 · 회생 미션
- 단위 테스트: `incar-app/src/test/.../JudgeEngineTest.kt` (11개)

## 도로 · 법규 데이터
| 데이터 | 출처 | 쓰임 |
|---|---|---|
| 어린이보호구역 | 공공데이터포털 전국어린이보호구역표준데이터 (`tn_pubr_public_child_prtc_zn_api`, 14,642건) | 스쿨존 스텔스 / 스쿨존 침공 |
| 제한속도 | 공공데이터포털 전국무인교통단속카메라표준데이터 (`tn_pubr_public_unmanned_traffic_camera_api`, 44,650건)의 `lmttVe` | 리미트 키퍼 / 과속 스캔들 |
| 돌발상황 | 국가교통정보센터(ITS) `eventInfo` — 주행 중에만 15분(또는 5km)마다, 월 900건에서 정지 | 세이프 패스 |
| 신호 · 정지선 · 횡단보도 | 실시간 공개 API 없음 → `SignalProvider` 인터페이스, 데모 시나리오로 시연 | 레드카드 · 정지선 · 옐로 브레이크 · 우회전 패싱 |

API 키는 `local.properties`에만 둡니다 (`DATA_GO_KR_KEY`, `ITS_API_KEY`).

## 구조
```
incar-app/src/main/java/com/karmadrive/incar/
├─ signal/  PleosVehicleSource(Pleos Vehicle SDK) · CarApiSource(AAOS VHAL) · ReplaySource(시나리오)
├─ run/     RunRecorder — 기어 D 시작 · P 종료, 흑역사 즉시 트윈카 반영
├─ engine/  JudgeEngine v2 (순수 Kotlin) · 규칙 · 모델
├─ game/    Scars(부위 · 상처 · 사유) · TwinLook(그림 결정) · 무드 · 성장 · MBTI · 퀘스트
├─ share/   LiveShareServer — 라이브 트윈 공유 (GET / · /api/state, POST /api/cheer · /api/repair)
├─ data/    KarmaStore — KP 원장 · 트윈카 상처 · 응원 (모의 서버)
├─ road/    RoadDataHub — 어린이보호구역 · 단속카메라 · ITS 돌발 · SignalProvider
├─ voice/   TwinVoice(TTS) · VoiceCommandReceiver(App Schema 함수)
└─ ui/      Compose 화면 IC01~IC09 + 트윈카 정비소
incar-app/src/main/assets/live/   라이브 트윈 웹 (PC 브라우저 · KARMA 앱 공용)
mobile-app/                        KARMA 앱(스마트폰) WebView 프로토타입
```

## 그림 출처
트윈카 · 카르마 드라이버(고릴라) 일러스트는 화면 기획안에서 쓴 KARMA DRIVE 자체 이미지입니다.

## 보안
공공데이터포털 · ITS 키, Pleos Client ID · Client Secret · Project ID는 `local.properties`에만 둡니다(커밋 금지).
