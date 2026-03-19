# CustomDamageSystem

Minecraft 1.21.4 커스텀 데미지/스탯 시스템. Fabric 모드 + Paper 플러그인 이중 구조.

## 프로젝트 구조

```
CustomDamageSystem/
├── CustomDamageSystem/          # Fabric 모드 (클라이언트 + 서버)
│   ├── src/main/                # 서버 사이드 (Mixin, 스탯, 전투)
│   └── src/client/              # 클라이언트 사이드 (HUD, UI, 렌더링)
├── CustomDamageSystem-plugin/   # Paper 플러그인 (서버 전용)
└── AngongGui/                   # 별도 GUI 모드 (클라이언트)
```

### 동작 환경

| 환경 | 서버 | 클라이언트 |
|------|------|-----------|
| Paper + Fabric 클라이언트 | 플러그인이 전투/스탯 계산 | 모드가 HUD/UI 표시 |
| Fabric 서버 | 모드가 전투/스탯 계산 | 모드가 HUD/UI 표시 |
| 싱글플레이 | 모드(통합 서버) | 모드(동일 프로세스) |

---

## 주요 시스템

### 1. 커스텀 스탯 시스템

플레이어별 스탯을 JSON으로 관리. 장비 장착 시 자동 재계산.

**스탯 목록:**
- **기초**: 힘(STR), 민첩(AGI), 지능(INT), 행운(LUK)
- **전투**: 공격력, 마법공격력, 방어력, HP, MP
- **특수**: 크리티컬 확률/피해, 방관, 추가데미지, 속성배율, 생명력흡수
- **유틸**: 이동속도, 버프지속, 쿨타임감소, 골드보너스

**기초 스탯 → 전투 스탯 환산** (`enableBaseStatScaling: true` 시):
```
힘 → 공격력 ×2.5, HP ×10
민첩 → 이동속도 ×0.05, 크리율 ×0.02
지능 → 마공 ×3.0, MP ×5.0, 쿨감 ×0.01
행운 → 크리율 ×0.03, 크뎀 ×0.1, 골드보너스 ×0.05
```

### 2. 커스텀 데미지 계산

```
원시 데미지 = (공격력 + 보너스데미지 + 장비보너스) × 차지배율
관통 = max(0, 적방어 - 방관)
경감률 = 관통 / (관통 + 500)
최종 = 원시 × (1 - 경감률) × 속성배율 × [크리 시 크뎀%]
```

**근거리 vs 원거리:**
- 근거리: 바닐라 무기 공격력 보너스 + 공격 차지 배율 적용
- 원거리(활/쇠뇌): 바닐라 투사체 데미지를 `extraFlat` 보너스로 추가, 차지배율 1.0

### 3. 흡수(Absorption) 시스템

황금사과/마법 황금사과 섭취 시:

```
총 혜택 = 6 (MAX_ABSORPTION, 고정값)
1) 부족한 HP 먼저 회복 (최대 6까지)
2) 나머지를 흡수 HP로 추가 (최대 6 캡)

예) HP 9/10 → 1 회복 + 흡수 5
예) HP 10/10 → 흡수 6
예) HP 3/10 → 6 회복 (HP→9), 흡수 0
```

- **maxHp 변경 없음** (이전: maxHp에 바닐라 16을 더해 무한 누적 버그)
- **데미지 순서**: 흡수 HP → 실제 HP (흡수가 방패 역할)
- **재섭취**: 기존 흡수 유지, 캡(6) 이내에서만 보충
- **효과 만료**: 흡수 HP 즉시 제거
- **HUD 표시**: 노란색 바 + `+N` 텍스트

### 4. 아이템 등록 시스템 (`/zcds`)

커맨드로 아이템을 등록하고 스탯을 부여하는 시스템.

```
/zcds register <type> <id>   # 손에 든 아이템 등록
/zcds unregister <id>        # 등록 해제
/zcds list                   # 등록 목록
/zcds give <player> <id>     # 아이템 지급
```

**타입:** `weapon`, `ring`, `necklace`, `earring`, `helmet`, `chestplate`, `leggings`, `boots`

**아이템 등록 시:**
- `customdamagesystem-item-stats.json`에 스탯 엔트리 추가
- `customdamagesystem:registry_id` NBT 태그 삽입 (등록 마커)
- Oraxen 아이템/CustomModelData 호환
- 원본 아이템 Base64(플러그인)/SNBT(모드) 직렬화 저장

### 5. 아이템 레벨 시스템

`/zcds register`로 등록된 아이템에만 아이템 레벨 표시.

- **서버**: `registryId → level` 매핑을 클라이언트에 전송
- **클라이언트**: 아이템 NBT에 `registry_id` 태그가 있는 아이템만 툴팁에 레벨 표시
- 일반 바닐라 아이템에는 레벨이 뜨지 않음 (의도적)

**아이템 레벨 = 장비 4슬롯 평균** (로스트아크 방식):
```
슬롯1(무기) + 슬롯2(머리) + 슬롯3(몸통) + 슬롯4(하의) / 4
```

### 6. 악세서리 시스템

반지/목걸이/귀걸이/무기를 장착하는 전용 UI.

- 클라이언트: `AccessoryScreen` + `AccessoryMenu` (인벤토리 탭에서 전환)
- 서버: `AccessoryDataManager`로 플레이어별 악세서리 JSON 저장
- 악세서리 스탯(힘/민첩/지능/행운)이 전투 스탯에 반영
- 플러그인 서버: Adventure `GsonComponentSerializer`로 아이템명/로어 직렬화 → 클라이언트에서 `Component.Serializer.fromJson()`으로 파싱

### 7. 데미지 넘버 렌더링

월드 내 3D 공간에 데미지 수치를 표시.

- **스프라이트 기반**: 데미지 스킨 시스템 (기본 + 커스텀 팩)
- **범위 제한** (서버 설정 가능):
  - XZ: 25블록 (기본)
  - Y: ±10블록 (기본)
- **타입별 색상**: 물리(빨강), 마법(파랑), 크리(노랑), 힐(초록)
- 스킨 전환: `Ctrl+K` 또는 서버 커맨드

### 8. 전투/생활 모드

- **생활 모드**: 일반 인벤토리 + 악세서리 탭
- **전투 모드**: 전용 퀵슬롯 UI (핫바 숨김, 커스텀 슬롯 3개)
- 모드 전환: `R` 키 (기본)

### 9. HUD 오버레이

바닐라 하트/갑옷/허기/경험치/핫바 완전 숨김 → 커스텀 UI 대체.

```
┌─────┬─────┐
│  H  │  M  │
│     │     │  ← 세로 바 (HP: 빨강, MP: 파랑)
│ 현재│ 현재│
│/최대│/최대│  ← 텍스트 크기 0.45x
│+흡수│     │  ← 노란색 (흡수 있을 때만)
└─────┴─────┘
```

- HP 25% 이하: 빨강 → 주황색으로 변경
- 흡수 HP: 노란색 바가 빨간 바 상단에 오버레이

---

## 설정 파일

### 서버 설정 (`customdamagesystem-server.json`)

```json
{
  "defaultHitCooldownTicks": 7,
  "defenseConstant": 500.0,
  "environmentalDamageScale": 1.0,
  "includeVanillaArmorForPlayerDefense": true,
  "vanillaArmorDefenseMultiplier": 10.0,
  "enableBaseStatScaling": true,
  "strToAttack": 2.5,
  "strToHp": 10.0,
  "agiToMoveSpeed": 0.05,
  "agiToCritRate": 0.02,
  "intToMagicAttack": 3.0,
  "intToMp": 5.0,
  "lukToCritRate": 0.03,
  "lukToCritDamage": 0.1,
  "lukToGoldBonus": 0.05,
  "damageNumberRangeXZ": 25.0,
  "damageNumberRangeY": 10.0
}
```

### 아이템 스탯 설정 (`customdamagesystem-item-stats.json`)

```json
{
  "items": [
    {
      "itemId": "minecraft:diamond_sword",
      "registryId": "my_diamond_sword",
      "slots": ["mainhand"],
      "itemLevel": 8.0,
      "itemLevelSlot": 1,
      "overrideVanillaMainhand": true,
      "attack": 25.0,
      "critRate": 60.0,
      "critDamage": 18.0,
      "attackSpeed": 4.0,
      "weapons": 1
    }
  ]
}
```

### 클라이언트 설정 (`customdamagesystem-client.json`)

```json
{
  "enableStatScreen": true,
  "statScreenKey": "J"
}
```

---

## 네트워크 프로토콜

**채널:** `customdamagesystem:stat` (JSON over CustomPayload)

### 서버 → 클라이언트 액션

| 액션 | 설명 |
|------|------|
| `stat_data` | 전체 스탯 동기화 (접속 시, 장비 변경 시) |
| `hp_update` | HP/MP/흡수HP 변경 알림 |
| `damage_number` | 데미지 넘버 렌더링 요청 (좌표, 금액, 크리, 타입) |
| `buff_update` | 활성 버프 목록 |
| `item_levels` | `registryId → level` 매핑 + 무기 목록 |
| `skin_list` | 보유 데미지 스킨 목록 |
| `skin_change` | 플레이어 스킨 변경 알림 |
| `economy_update` | 골드 동기화 |
| `accessory_data` | 악세서리 슬롯 데이터 (아이템 + 로어) |
| `accessory_registry_sync` | 악세서리 레지스트리 동기화 |

### 클라이언트 → 서버 액션

| 액션 | 설명 |
|------|------|
| `get` | 전체 스탯 요청 |
| `skin_cycle` | 스킨 순환 요청 |
| `skin_select` | 특정 스킨 선택 |

---

## 외부 모드 연동 API

```java
import org.zeripe.angongserverside.api.CustomDamageApi;

// 스탯 설정
CustomDamageApi.setAttack(player, 120);
CustomDamageApi.setStrength(player, 45);
CustomDamageApi.setArmorPenetration(player, 35);
CustomDamageApi.setLifeSteal(player, 8.5);

// 무기별 쿨다운
CustomDamageApi.registerWeaponHitCooldown(Items.DIAMOND_SWORD, 10);
```

---

## 빌드

### Fabric 모드

```bash
cd CustomDamageSystem
./gradlew build
# 출력: build/libs/customdamagesystem-*.jar
```

### Paper 플러그인

```bash
cd CustomDamageSystem-plugin
./gradlew build
# 출력: build/libs/CustomDamageSystem-plugin-*.jar
```

---

## 데이터 저장

현재 모든 데이터는 JSON 파일로 저장됩니다.

| 데이터 | 저장 위치 |
|--------|----------|
| 플레이어 스탯 | `playerdata/<uuid>.json` |
| 악세서리 | `accessories/<uuid>.json` |
| 데미지 스킨 | `damage_skins/<uuid>.json` |
| 아이템 설정 | `customdamagesystem-item-stats.json` |
| 서버 설정 | `customdamagesystem-server.json` |

> **MySQL 지원**: 설계 완료, 미구현. `PlayerDataStorage` 인터페이스 기반으로 `JsonPlayerDataStorage` ↔ `MysqlPlayerDataStorage` 전환 예정.

---

## 기술 스택

- **Minecraft**: 1.21.4
- **Fabric**: Loader + Fabric API
- **Paper**: 1.21.4-R0.1-SNAPSHOT
- **Mojang Mappings**: 디컴파일 매핑
- **Mixin**: 바닐라 코드 후킹 (HUD 숨김, 흡수, 회복, 사망 등)
- **Adventure API**: Paper 서버 Component 직렬화
- **Java**: 21

---

## 라이선스

All Rights Reserved © zeripe
