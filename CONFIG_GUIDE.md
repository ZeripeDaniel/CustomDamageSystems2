# CustomDamageSystem 설정 가이드

안공놀이터 커스텀 대미지 시스템의 전체 설정 파일 및 모드 사용법을 설명합니다.

> **모든 설정 파일은 서버/클라이언트 최초 실행 시 자동 생성됩니다.**
> 수정 후 서버(또는 클라이언트) 재시작이 필요합니다.

---

## 목차

1. [설정 파일 위치](#설정-파일-위치)
2. [서버 설정 (customdamagesystem-server.json)](#1-서버-설정)
3. [아이템 스탯 설정 (customdamagesystem-item-stats.json)](#2-아이템-스탯-설정)
4. [스탯 공식 설정 (customdamagesystem-stat.json)](#3-스탯-공식-설정)
5. [몬스터 공격 그룹 설정 (customdamagesystem-monster-groups.json)](#4-몬스터-공격-그룹-설정)
6. [데미지 스킨 설정 (customdamagesystem-damage-skins.json)](#5-데미지-스킨-설정)
7. [클라이언트 UI 설정 (customdamagesystem-client.json)](#6-클라이언트-ui-설정)
8. [인게임 조작법](#인게임-조작법)
9. [악세사리 & 무기 시스템](#악세사리--무기-시스템)
10. [전투 모드](#전투-모드)
11. [데미지 스킨 시스템](#데미지-스킨-시스템)
12. [API (외부 연동)](#api-외부-연동)

---

## 설정 파일 위치

| 플랫폼 | 경로 |
|--------|------|
| Fabric 모드 서버 | `config/` (서버 루트의 config 폴더) |
| Paper 플러그인 | `plugins/CustomDamageSystem/` (플러그인 데이터 폴더) |
| 클라이언트 | `config/` (마인크래프트 인스턴스의 config 폴더) |

---

## 1. 서버 설정

**파일명:** `customdamagesystem-server.json`

서버 전투 시스템의 핵심 수치를 조정합니다.

```json
{
  "defaultHitCooldownTicks": 7,
  "equipmentSyncIntervalTicks": 20,
  "useVanillaAttackSpeedForHitCooldown": true,
  "minAttackSpeedCooldownTicks": 1,
  "maxAttackSpeedCooldownTicks": 40,
  "defenseConstant": 500.0,
  "environmentalDamageScale": 1.0,
  "externalDamageUsesCustomDefense": false,
  "includeVanillaArmorForPlayerDefense": true,
  "vanillaArmorDefenseMultiplier": 10.0
}
```

### 필드 설명

| 필드 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `defaultHitCooldownTicks` | int | 7 | 기본 히트 쿨다운 (틱 단위, 20틱 = 1초) |
| `equipmentSyncIntervalTicks` | int | 20 | 장비 스탯 동기화 주기 (틱) |
| `useVanillaAttackSpeedForHitCooldown` | bool | true | 바닐라 공격 속도를 쿨다운 계산에 사용할지 여부 |
| `minAttackSpeedCooldownTicks` | int | 1 | 공격 속도 기반 최소 쿨다운 |
| `maxAttackSpeedCooldownTicks` | int | 40 | 공격 속도 기반 최대 쿨다운 |
| `defenseConstant` | double | 500.0 | 방어력 감소 공식의 상수. `감소율 = 방어력 / (방어력 + 상수)` |
| `environmentalDamageScale` | double | 1.0 | 환경 데미지 (낙하, 용암 등) 배율 |
| `externalDamageUsesCustomDefense` | bool | false | 외부 데미지에 커스텀 방어력 적용 여부 |
| `includeVanillaArmorForPlayerDefense` | bool | true | 바닐라 갑옷 값을 방어력에 포함할지 |
| `vanillaArmorDefenseMultiplier` | double | 10.0 | 바닐라 갑옷 → 방어력 변환 배율 (갑옷값 × 배율 = 추가 방어력) |

### 방어력 공식
```
감소율 = 유효방어력 / (유효방어력 + defenseConstant)
최종 데미지 = 원본 데미지 × (1 - 감소율)
```
예: 방어력 500, 상수 500 → 감소율 50% → 데미지 절반

---

## 2. 아이템 스탯 설정

**파일명:** `customdamagesystem-item-stats.json`

아이템별 기본 스탯을 정의합니다. 여기에 등록된 아이템만 커스텀 스탯 시스템의 영향을 받습니다.

```json
{
  "items": [
    {
      "itemId": "minecraft:wooden_sword",
      "slots": ["mainhand"],
      "itemLevel": 1.0,
      "itemLevelSlot": 1,
      "overrideVanillaMainhand": true,
      "overrideVanillaArmor": false,
      "attack": 8.0,
      "magicAttack": 0.0,
      "defense": 0.0,
      "hp": 0.0,
      "critRate": 75.0,
      "critDamage": 10.0,
      "attackSpeed": 4.0,
      "cooldownReduction": 0.0,
      "moveSpeed": 0.0,
      "strength": 0,
      "agility": 0,
      "intelligence": 0,
      "luck": 0,
      "weapons": 1
    }
  ]
}
```

### 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| `itemId` | string | 아이템 ID (예: `"minecraft:diamond_sword"`, `"customdamagesystem:wooden_ring"`) |
| `slots` | string[] | 적용 슬롯: `"mainhand"`, `"head"`, `"chest"`, `"legs"`, `"feet"`, `"offhand"`, `"ring"`, `"necklace"`, `"earring"` |
| `itemLevel` | double | 아이템 레벨 (전투력 계산 및 표시용) |
| `itemLevelSlot` | int | 아이템 레벨이 적용되는 슬롯 티어 |
| `overrideVanillaMainhand` | bool | `true`면 바닐라 메인핸드 공격력 무시 (커스텀 스탯만 사용) |
| `overrideVanillaArmor` | bool | `true`면 바닐라 방어구 수치 무시 |
| `attack` | double | 물리 공격력 |
| `magicAttack` | double | 마법 공격력 |
| `defense` | double | 방어력 |
| `hp` | double | 추가 체력 |
| `critRate` | double | 치명타 확률 (%) |
| `critDamage` | double | 치명타 데미지 추가 (%) |
| `attackSpeed` | double | 공격 속도 (높을수록 빠름) |
| `cooldownReduction` | double | 쿨타임 감소 (%) |
| `moveSpeed` | double | 이동속도 보너스 (%) |
| `strength` | int | 힘 스탯 |
| `agility` | int | 민첩 스탯 |
| `intelligence` | int | 지능 스탯 |
| `luck` | int | 행운 스탯 |
| **`weapons`** | **int** | **`1`이면 무기 슬롯에 장착 가능, `0`이면 불가** |

### 무기 등록 방법
아이템을 무기 탭(악세사리 인벤토리 슬롯 6번)에 장착하려면 **반드시** `"weapons": 1`로 설정해야 합니다.

```json
{
  "itemId": "minecraft:diamond_sword",
  "slots": ["mainhand"],
  "weapons": 1,
  "attack": 25.0
}
```

### 새 아이템 추가 예시
Oraxen 등 커스텀 아이템 플러그인의 아이템도 네임스페이스:아이템ID 형태로 추가 가능합니다.
```json
{
  "itemId": "oraxen:fire_blade",
  "slots": ["mainhand"],
  "itemLevel": 15.0,
  "overrideVanillaMainhand": true,
  "attack": 50.0,
  "magicAttack": 20.0,
  "critRate": 10.0,
  "critDamage": 30.0,
  "strength": 5,
  "weapons": 1
}
```

---

## 3. 스탯 공식 설정

**파일명:** `customdamagesystem-stat.json`

기본 스탯(힘/민첩/지능/행운)이 전투 수치에 어떻게 영향을 미치는지 정의합니다.

```json
{
  "baseHp": 10,
  "baseMp": 0,
  "enableBaseStatScaling": true,
  "strToAttack": 2.5,
  "strToHp": 2.0,
  "agiToMoveSpeed": 0.05,
  "agiToCritRate": 0.02,
  "intToMagicAttack": 3.0,
  "intToMp": 2.0,
  "intToCooldownReduction": 0.01,
  "lukToCritRate": 0.03,
  "lukToCritDamage": 0.1,
  "lukToGoldBonus": 0.05,
  "customRules": [
    {
      "sourceStat": "strength",
      "targetAttribute": "maxHp",
      "perPoints": 5.0,
      "gain": 10.0
    }
  ]
}
```

### 기본 계수

| 필드 | 설명 | 예시 |
|------|------|------|
| `baseHp` | 기본 최대 체력 | 10 |
| `baseMp` | 기본 최대 마나 | 0 |
| `enableBaseStatScaling` | 스탯 스케일링 활성화 여부 | true |
| `strToAttack` | 힘 1당 공격력 증가 | 2.5 → 힘 10 = 공격력 +25 |
| `strToHp` | 힘 1당 체력 증가 | 2.0 → 힘 10 = 체력 +20 |
| `agiToMoveSpeed` | 민첩 1당 이동속도 증가 (%) | 0.05 |
| `agiToCritRate` | 민첩 1당 치명타 확률 증가 (%) | 0.02 |
| `intToMagicAttack` | 지능 1당 마법 공격력 증가 | 3.0 |
| `intToMp` | 지능 1당 마나 증가 | 2.0 |
| `intToCooldownReduction` | 지능 1당 쿨타임 감소 (%) | 0.01 |
| `lukToCritRate` | 행운 1당 치명타 확률 증가 (%) | 0.03 |
| `lukToCritDamage` | 행운 1당 치명타 데미지 증가 (%) | 0.1 |
| `lukToGoldBonus` | 행운 1당 골드 보너스 (%) | 0.05 |

### customRules (고급 커스텀 규칙)

기본 계수 외에 추가적인 스케일링 규칙을 정의합니다.

```json
{
  "sourceStat": "strength",
  "targetAttribute": "maxHp",
  "perPoints": 5.0,
  "gain": 10.0
}
```
→ **힘 5포인트당 최대 체력 +10**

| 필드 | 설명 |
|------|------|
| `sourceStat` | 원본 스탯: `"strength"`, `"agility"`, `"intelligence"`, `"luck"` |
| `targetAttribute` | 대상 속성 (아래 목록 참조) |
| `perPoints` | 몇 포인트당 |
| `gain` | 증가량 |

**targetAttribute 사용 가능 값:**
`maxHp`, `maxMp`, `attack`, `magicAttack`, `defense`, `critRate`, `critDamage`, `armorPenetration`, `bonusDamage`, `elementalMultiplier`, `lifeSteal`, `attackMultiplier`, `magicAttackMultiplier`, `moveSpeed`, `buffDuration`, `cooldownReduction`, `clearGoldBonus`

---

## 4. 몬스터 공격 그룹 설정

**파일명:** `customdamagesystem-monster-groups.json`

여러 몬스터가 동시에 플레이어를 공격할 때 히트 쿨다운을 개별로 적용할지 결정합니다.

```json
{
  "independentAttackers": ["*"]
}
```

### 설정 방법

| 값 | 동작 |
|---|------|
| `["*"]` | **모든 엔티티** 개별 쿨다운 (좀비 3마리가 동시에 때릴 수 있음) |
| `["minecraft:zombie", "minecraft:skeleton"]` | 좀비, 스켈레톤만 개별 쿨다운 |
| `[]` | 모든 몬스터가 공유 쿨다운 (1마리가 때리면 다른 몬스터도 쿨다운 공유) |

---

## 5. 데미지 스킨 설정

**파일명:** `customdamagesystem-damage-skins.json`

서버에서 관리하는 데미지 스킨 팩을 등록합니다.

```json
{
  "cellWidth": 16,
  "cellHeight": 24,
  "packs": [
    {
      "id": "default",
      "displayName": "기본 스킨",
      "namespace": "customdamagesystem",
      "texturePath": "textures/gui/damage_skins/default/",
      "cellWidth": 0,
      "cellHeight": 0,
      "freeForAll": true
    }
  ]
}
```

### 글로벌 설정

| 필드 | 설명 |
|------|------|
| `cellWidth` | 스프라이트 시트 한 칸의 가로 크기 (px) |
| `cellHeight` | 스프라이트 시트 한 칸의 세로 크기 (px) |

### 스킨 팩 (packs) 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | string | 스킨 고유 ID (영문, 소문자 권장) |
| `displayName` | string | 인게임 표시 이름 |
| `namespace` | string | 리소스팩 네임스페이스 (기본: `"customdamagesystem"`) |
| `texturePath` | string | 텍스처 기본 경로 (이 경로 아래에 4장의 PNG 필요) |
| `cellWidth` | int | 이 스킨 전용 셀 너비 (`0`이면 글로벌 설정 사용) |
| `cellHeight` | int | 이 스킨 전용 셀 높이 (`0`이면 글로벌 설정 사용) |
| `freeForAll` | bool | `true`: 모든 플레이어에게 자동 부여 / `false`: 개별 부여 필요 (상점용) |

### 스킨 팩 추가 방법

1. **텍스처 4장 준비** (스프라이트 시트)
   - `physical.png` — 일반 물리 데미지
   - `magical.png` — 마법 데미지
   - `critical.png` — 치명타 데미지
   - `heal.png` — 회복

2. **스프라이트 시트 규격**
   - 가로로 **13칸**: `0 1 2 3 4 5 6 7 8 9 , ! +`
   - 기본 셀 크기: 16×24 px → 전체 이미지: 208×24 px
   - 셀 크기를 변경하려면 `cellWidth`/`cellHeight` 설정

3. **리소스팩에 텍스처 배치**
   ```
   assets/{namespace}/{texturePath}/physical.png
   assets/{namespace}/{texturePath}/magical.png
   assets/{namespace}/{texturePath}/critical.png
   assets/{namespace}/{texturePath}/heal.png
   ```

4. **JSON에 팩 추가**
   ```json
   {
     "id": "fire",
     "displayName": "불꽃 스킨",
     "namespace": "customdamagesystem",
     "texturePath": "textures/gui/damage_skins/fire/",
     "freeForAll": false
   }
   ```

5. **리소스팩 적용** — 서버 리소스팩으로 배포하거나 클라이언트에 직접 설치

### 플레이어별 소유 데이터

플레이어별 스킨 소유/선택 데이터는 자동으로 저장됩니다:
- **Fabric 모드:** `config/damage_skins/{UUID}.json`
- **Paper 플러그인:** `plugins/CustomDamageSystem/damage_skins/{UUID}.json`

---

## 6. 클라이언트 UI 설정

**파일명:** `customdamagesystem-client.json`

클라이언트 전용 UI 설정입니다. 서버 스탯에는 영향을 주지 않습니다.

```json
{
  "enableStatScreen": true,
  "statScreenKey": "J"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `enableStatScreen` | bool | 스탯 화면 활성화 여부 |
| `statScreenKey` | string | 스탯 화면 열기 키 (키 이름, 예: `"J"`, `"K"`, `"TAB"`) |

---

## 인게임 조작법

| 키 | 동작 |
|----|------|
| **J** (설정 변경 가능) | 스탯 화면 열기/닫기 |
| **Ctrl + `** (백틱) | 전투 모드 전환 |
| **Ctrl + K** | 데미지 스킨 전환 (소유한 스킨 순환) |

---

## 악세사리 & 무기 시스템

### 악세사리 인벤토리 슬롯 구조

| 슬롯 인덱스 | 타입 | 설명 |
|-------------|------|------|
| 0 | RING | 반지 1 |
| 1 | RING | 반지 2 |
| 2 | NECKLACE | 목걸이 |
| 3 | EARRING | 귀걸이 1 |
| 4 | EARRING | 귀걸이 2 |
| 5 | WEAPON | 무기 |

### 무기 장착 조건
- `customdamagesystem-item-stats.json`에서 해당 아이템의 `"weapons": 1`로 설정되어 있어야 함
- 등록되지 않은 아이템은 무기 슬롯에 배치 불가
- 무기 슬롯에는 1개만 장착 가능 (스택 불가)

### 악세사리 장착 조건
- 모드 자체 아이템 (반지, 목걸이, 귀걸이)은 자동 등록됨
- 외부 아이템을 악세사리로 사용하려면 코드 또는 API로 등록 필요

---

## 전투 모드

**Ctrl + `** 로 전투 모드를 전환합니다.

### 전투 모드 진입 시
- **메인핸드** → 악세사리 인벤토리의 무기 슬롯에 장착된 무기로 교체
- **오프핸드** → 토치 자동 장착
- 기존 메인핸드/오프핸드 아이템은 서버에 임시 저장됨

### 전투 모드 해제 시
- 메인핸드/오프핸드가 원래 아이템으로 복원됨

### 주의사항
- 무기 슬롯이 비어있으면 전투 모드 진입 불가
- 접속 종료 시 자동으로 전투 모드 해제됨

---

## 데미지 스킨 시스템

### 서버 주도 모드 (권장)
서버에 모드 또는 플러그인이 설치되어 있으면 자동으로 서버 모드로 동작합니다.

- 서버가 `skin_list` 패킷으로 사용 가능한 스킨 목록을 전송
- 플레이어의 스킨 선택은 서버에 저장되고 모든 플레이어에게 브로드캐스트
- **내가 선택한 스킨이 다른 플레이어에게도 보임**
- `freeForAll: true` 스킨은 접속 시 자동 부여
- `freeForAll: false` 스킨은 API로 개별 부여 필요 (상점 시스템 연동)

### 로컬 모드 (폴백)
서버에 모드/플러그인이 없으면 클라이언트 로컬에서 기본 스킨만 사용합니다.

### 스킨 전환
- **인게임:** Ctrl + K
- 소유한 스킨 → 텍스트 모드 → 소유한 스킨 순으로 순환

---

## API (외부 연동)

다른 모드/플러그인에서 이 시스템을 연동하려면 아래 API를 사용합니다.

### 스탯 API
```java
// 플레이어 스탯 데이터 조회
CustomDamageApi.getStatManager().getData(uuid);

// 악세사리 등록
AccessoryApi.registerAccessory(item, AccessoryType.RING, strength, agility, intelligence, luck);
```

### 데미지 스킨 API (서버 측)
```java
DamageSkinManager skinManager = ...; // 메인 클래스에서 참조

// 스킨 소유권 부여 (상점 구매 후 호출)
skinManager.grantSkin(playerUuid, "fire");

// 스킨 소유권 제거
skinManager.revokeSkin(playerUuid, "fire");

// 소유 여부 확인
skinManager.hasSkin(playerUuid, "fire");

// 강제 스킨 선택
skinManager.selectSkin(playerUuid, "fire");

// 현재 선택 스킨 조회
skinManager.getSelectedSkin(playerUuid); // → "fire" 또는 "none"
```

### 네트워크 프로토콜 (UI 모드 연동)
기존 `customdamagesystem:stat` 채널을 통해 JSON 패킷으로 통신합니다.

**클라이언트 → 서버:**
```json
{"action": "skin_cycle"}
{"action": "skin_select", "skinId": "fire"}
```

**서버 → 클라이언트:**
```json
{"action": "skin_list", "cellWidth": 16, "cellHeight": 24, "packs": [...], "owned": [...], "selected": "default"}
{"action": "skin_change", "uuid": "...", "skinId": "fire"}
```

---

## 자주 묻는 질문

### Q: 무기를 무기 슬롯에 넣을 수 없어요
→ `customdamagesystem-item-stats.json`에서 해당 아이템의 `"weapons": 1`로 설정했는지 확인하세요. 설정 후 서버 재시작이 필요합니다.

### Q: Oraxen/ItemsAdder 커스텀 아이템도 등록할 수 있나요?
→ 네. `customdamagesystem-item-stats.json`에 해당 아이템의 ID를 추가하면 됩니다. (예: `"oraxen:fire_blade"`)

### Q: 데미지 스킨이 안 보여요
→ 리소스팩에 텍스처 4장(physical/magical/critical/heal.png)이 올바른 경로에 있는지 확인하세요. 스프라이트 시트는 가로 13칸(208×24px 기본)이어야 합니다.

### Q: 특정 플레이어에게만 스킨을 줄 수 있나요?
→ JSON에서 `"freeForAll": false`로 설정하고, 서버 코드에서 `skinManager.grantSkin(uuid, "skinId")`를 호출하세요. 상점 플러그인과 연동하여 구매 시 호출하면 됩니다.

### Q: 설정 파일이 없어요
→ 서버/클라이언트를 한 번 실행하면 기본 설정 파일이 자동 생성됩니다.
