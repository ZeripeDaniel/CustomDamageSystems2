package org.zeripe.angongserverside.economy;

import java.util.UUID;

/**
 * 경제 시스템 추상화 인터페이스.
 * internal / scoreboard 모드를 지원한다.
 */
public interface EconomyProvider {

    long getBalance(UUID uuid);

    boolean deposit(UUID uuid, long amount);

    boolean withdraw(UUID uuid, long amount);

    void setBalance(UUID uuid, long amount);
}
