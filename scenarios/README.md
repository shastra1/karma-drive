# 시나리오

앱 안의 각본(`signal/ReplaySource.kt`의 `Scenarios`)입니다. 허브의 데모 주행 버튼으로 재생합니다.

| 시나리오 | 내용 | 결과 (단위 테스트로 확인) |
|---|---|---|
| 출근길 S등급 | 벨트 → D → 차선 변경 3회(깜빡이) → 와이퍼 ON → 우회전 퍼펙트 스톱 → P | S등급 · 데미지 0 |
| 흑역사 주행 | 벨트 미착용 → 급가속 → 깜빡이 없이 방향 전환 2회 → 급정거 → P | D등급 · 경고 1 · 데미지 13 |
| 법규 데모 (도심) | 적색 정지선 앞 정차 → 스쿨존 28km/h → 제한 50에서 65km/h → 공사 구간 서행 → 황색불 정지 → P | A등급 · 데미지 5 (과속 스캔들(소)) |
| 트윈카 수난 시대 (라이브 공유) | 스쿨존 45km/h → 횡단보도 우회전 20km/h → 적색 통과 → 78km/h → 깜빡이 없이 차선 변경 | D등급 · 데미지 25(상한) · 상처 5곳 (보닛 · 펜더 · 헤드램프 · 앞 유리 · 도어) |

## Virtual Console 수동 시나리오 (실제 신호 · Pleos Vehicle SDK)
1. 안전벨트 착용, Gear P
2. Gear D → ACCEL로 30km/h 이상
3. Turn Signal Blink left → Steering Angle 20° 이상 → 0° → Turn Signal Off  (깜빡이 콤보)
4. Turn Signal Off 상태에서 Steering Angle 20° 이상 (깜빡이 실종 → 도어 긁힘)
5. Blink right → BRAKE로 정지 1초 이상 → ACCEL + Steering 20° 이상 (퍼펙트 스톱)
6. Gear P → 리플레이 · 언박싱 · 흑역사 리포트 자동 진입
