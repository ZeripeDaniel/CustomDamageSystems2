package org.zeripe.angongserverside.economy;

import org.zeripe.angongserverside.combat.StatManager;

import java.util.UUID;

/**
 * 내부 DB(JSON/MySQL)에 저장된 골드를 직접 사용하는 이코노미.
 * StatManager의 gold 필드가 원본이다.
 */
public class InternalEconomyProvider implements EconomyProvider {

    private final StatManager statManager;

    public InternalEconomyProvider(StatManager statManager) {
        this.statManager = statManager;
    }

    @Override
    public long getBalance(UUID uuid) {
        return statManager.getGold(uuid);
    }

    @Override
    public boolean deposit(UUID uuid, long amount) {
        return statManager.addGoldByUuid(uuid, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, long amount) {
        return statManager.removeGoldByUuid(uuid, amount);
    }

    @Override
    public void setBalance(UUID uuid, long amount) {
        statManager.setGoldByUuid(uuid, amount);
    }
}
