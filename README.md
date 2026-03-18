# CustomDamageSystem (Unified)

이 프로젝트는 기존 `client`/`server` 분리 모드를 하나로 합친 통합 버전입니다.

## 목적

- 전용 서버에서 서버가 전투/스탯을 계산하고 클라이언트는 결과를 수신
- 싱글플레이에서도 동일한 모드 파일로 동작
- 외부 모드가 쉽게 참조할 수 있는 API 제공

## 설정 파일

서버 시작 시 아래 파일이 자동 생성됩니다.

- `config/customdamagesystem-server.json`
- `config/customdamagesystem-client.json`

기본 예시:

```json
{
  "defaultHitCooldownTicks": 7,
  "defenseConstant": 500.0,
  "environmentalDamageScale": 1.0,
  "externalDamageUsesCustomDefense": false,
  "includeVanillaArmorForPlayerDefense": true,
  "vanillaArmorDefenseMultiplier": 10.0,
  "enableBaseStatScaling": true,
  "strToAttack": 2.5,
  "strToHp": 10.0,
  "agiToMoveSpeed": 0.05,
  "agiToCritRate": 0.02,
  "intToMagicAttack": 3.0,
  "intToMp": 5.0,
  "intToCooldownReduction": 0.01,
  "lukToCritRate": 0.03,
  "lukToCritDamage": 0.1,
  "lukToGoldBonus": 0.05
}
```

- `enableBaseStatScaling`: `true`일 때만 힘/민첩/지능/행운이 전투 스탯으로 환산
- 기초 스탯 기본값은 0이며, 장비/NBT/외부 모드 API로 올리는 구조를 권장

클라이언트 예시:

```json
{
  "enableStatScreen": true,
  "statScreenKey": "J"
}
```

- `enableStatScreen`: `true/false`로 스탯창 사용 여부
- `statScreenKey`: 기본 `"J"` (문자열 키 이름)

## 외부 모드 연동 API

정적 API 클래스:

- `org.zeripe.angongserverside.api.CustomDamageApi`

제공 메서드:

- `setEquipStat(ServerPlayer player, String field, double value)`
- `setAttack(...)`, `setMagicAttack(...)`, `setDefense(...)`
- `setStrength(...)`, `setAgility(...)`, `setIntelligence(...)`, `setLuck(...)`
- `setCritRate(...)`, `setCritDamage(...)`
- `setArmorPenetration(...)`, `setBonusDamage(...)`
- `setElementalMultiplier(...)`, `setLifeSteal(...)`
- `setAttackMultiplier(...)`, `setMagicAttackMultiplier(...)`
- `registerWeaponHitCooldown(Item weapon, int ticks)`
- `unregisterWeaponHitCooldown(Item weapon)`

예시:

```java
CustomDamageApi.setAttack(player, 120);
CustomDamageApi.setStrength(player, 45);
CustomDamageApi.setArmorPenetration(player, 35);
CustomDamageApi.setAttackMultiplier(player, 125.0);
CustomDamageApi.setLifeSteal(player, 8.5);
CustomDamageApi.registerWeaponHitCooldown(Items.DIAMOND_SWORD, 10);
```

`setEquipStat` 필드 문자열 지원값:

- `attack`, `magicattack`, `defense`
- `critrate`, `critdamage`
- `armorpenetration`, `bonusdamage`
- `elementalmultiplier`, `lifesteal`
- `attackmultiplier`, `magicattackmultiplier`
- 기존 스탯(`strength`, `agility`, `intelligence`, `luck` 등)도 유지

## 네트워크 채널

- 채널 ID: `customdamagesystem:stat`
- 클라이언트가 `{"action":"get"}` 요청
- 서버가 `stat_data`, `hp_update`, `damage_number`, `buff_update` 응답

## 데미지 호환성

- 이 모드는 바닐라/타모드의 `entity.hurt(...)` 경로를 서버 이벤트에서 가로채 커스텀 처리한다.
- 외부(바닐라/타모드) 데미지는 기본적으로 들어온 데미지값을 기준으로 처리해 호환성을 우선한다.
- 플레이어 방어 계산 시 바닐라 방어구 수치를 커스텀 방어력에 합산할 수 있다.

