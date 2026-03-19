package org.zeripe.angongui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 주도 데미지 스킨 시스템 (클라이언트 측).
 *
 * 서버에서 받은 스킨 팩 목록을 관리하며,
 * 각 플레이어의 스킨 선택을 추적하여 다른 플레이어의 데미지도
 * 해당 플레이어가 선택한 스킨으로 표시한다.
 *
 * 스프라이트 시트: 가로 13칸 (0123456789,!+)
 */
public final class DamageSkin {
    private static final String CHAR_MAP = "0123456789,!+";
    private static final int CHAR_COUNT = CHAR_MAP.length();

    // ── 글로벌 설정 ──
    private static int defaultCellWidth = 16;
    private static int defaultCellHeight = 24;
    private static boolean serverMode = false;

    // ── 서버에서 받은 스킨 팩 목록 ──
    private static final List<SkinPackInfo> serverPacks = new ArrayList<>();

    // ── 로컬 등록 팩 (서버 미연결 시 폴백) ──
    private static final List<SkinPack> localPacks = new ArrayList<>();
    private static int localCurrentIndex = 0;
    private static boolean localEnabled = false;

    // ── 내 소유 스킨 & 선택 ──
    private static final Set<String> ownedSkins = new LinkedHashSet<>();
    private static String mySelectedSkin = "none";

    // ── 다른 플레이어의 스킨 선택 (UUID → skinId) ──
    private static final Map<UUID, String> playerSkinMap = new ConcurrentHashMap<>();

    private DamageSkin() {}

    // ════════════════════════════════════════
    //  서버 팩 정보 (네트워크에서 수신)
    // ════════════════════════════════════════

    public static class SkinPackInfo {
        public final String id;
        public final String displayName;
        public final String namespace;
        public final String texturePath;
        public final int cellWidth;
        public final int cellHeight;

        // 캐시된 ResourceLocation
        public final ResourceLocation physical;
        public final ResourceLocation magical;
        public final ResourceLocation critical;
        public final ResourceLocation heal;

        public SkinPackInfo(String id, String displayName, String namespace,
                            String texturePath, int cellWidth, int cellHeight) {
            this.id = id;
            this.displayName = displayName;
            this.namespace = namespace;
            this.texturePath = texturePath;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;

            String base = texturePath.endsWith("/") ? texturePath : texturePath + "/";
            this.physical = ResourceLocation.fromNamespaceAndPath(namespace, base + "physical.png");
            this.magical = ResourceLocation.fromNamespaceAndPath(namespace, base + "magical.png");
            this.critical = ResourceLocation.fromNamespaceAndPath(namespace, base + "critical.png");
            this.heal = ResourceLocation.fromNamespaceAndPath(namespace, base + "heal.png");
        }

        public ResourceLocation resolve(boolean crit, String type) {
            if (crit) return critical;
            return switch (type.toUpperCase()) {
                case "MAGICAL", "MAGIC" -> magical;
                case "HEAL" -> heal;
                default -> physical;
            };
        }
    }

    // ════════════════════════════════════════
    //  로컬 팩 (레거시 호환)
    // ════════════════════════════════════════

    public record SkinPack(
            String name,
            ResourceLocation physical,
            ResourceLocation magical,
            ResourceLocation critical,
            ResourceLocation heal
    ) {
        public ResourceLocation resolve(boolean crit, String type) {
            if (crit) return critical;
            return switch (type.toUpperCase()) {
                case "MAGICAL", "MAGIC" -> magical;
                case "HEAL" -> heal;
                default -> physical;
            };
        }
    }

    // ════════════════════════════════════════
    //  서버 모드 API (NetworkHandler에서 호출)
    // ════════════════════════════════════════

    /** 서버에서 받은 스킨 목록으로 초기화 */
    public static void setServerPacks(List<SkinPackInfo> packs, int cellW, int cellH,
                                       Set<String> owned, String selected) {
        serverPacks.clear();
        serverPacks.addAll(packs);
        defaultCellWidth = cellW;
        defaultCellHeight = cellH;
        ownedSkins.clear();
        ownedSkins.addAll(owned);
        mySelectedSkin = selected != null ? selected : "none";
        serverMode = true;
    }

    /** 다른 플레이어의 스킨 변경 수신 */
    public static void setPlayerSkin(UUID playerUuid, String skinId) {
        if ("none".equals(skinId)) {
            playerSkinMap.remove(playerUuid);
        } else {
            playerSkinMap.put(playerUuid, skinId);
        }
    }

    /** 내 스킨이 서버에서 확인된 변경 */
    public static void setMySelectedSkin(String skinId) {
        mySelectedSkin = skinId != null ? skinId : "none";
    }

    public static String getMySelectedSkin() {
        return mySelectedSkin;
    }

    public static Set<String> getOwnedSkins() {
        return Collections.unmodifiableSet(ownedSkins);
    }

    public static boolean isServerMode() {
        return serverMode;
    }

    // ════════════════════════════════════════
    //  로컬 모드 API (레거시 호환)
    // ════════════════════════════════════════

    public static void registerPack(String name) {
        String base = "textures/gui/damage_skins/" + name + "/";
        localPacks.add(new SkinPack(
                name,
                ResourceLocation.fromNamespaceAndPath("customdamagesystem", base + "physical.png"),
                ResourceLocation.fromNamespaceAndPath("customdamagesystem", base + "magical.png"),
                ResourceLocation.fromNamespaceAndPath("customdamagesystem", base + "critical.png"),
                ResourceLocation.fromNamespaceAndPath("customdamagesystem", base + "heal.png")
        ));
    }

    public static void enable(int cellW, int cellH) {
        defaultCellWidth = cellW;
        defaultCellHeight = cellH;
        localEnabled = true;
    }

    public static void disable() {
        localEnabled = false;
    }

    public static boolean isEnabled() {
        if (serverMode) {
            return !"none".equals(mySelectedSkin) && findServerPack(mySelectedSkin) != null;
        }
        return localEnabled && !localPacks.isEmpty();
    }

    /** 특정 skinId 로 렌더링 가능한지 확인 */
    public static boolean isEnabledForSkin(String skinId) {
        if (skinId == null || "none".equals(skinId)) return false;
        if (serverMode) return findServerPack(skinId) != null;
        // 로컬: 이름으로 검색
        return localPacks.stream().anyMatch(p -> p.name().equals(skinId));
    }

    public static int getCellWidth() { return defaultCellWidth; }
    public static int getCellHeight() { return defaultCellHeight; }

    /** 로컬 모드 다음 팩 전환 */
    public static String nextPack() {
        if (localPacks.isEmpty()) return "none";
        localCurrentIndex = (localCurrentIndex + 1) % localPacks.size();
        return localPacks.get(localCurrentIndex).name();
    }

    public static String currentPackName() {
        if (serverMode) return mySelectedSkin;
        if (localPacks.isEmpty()) return "none";
        return localPacks.get(localCurrentIndex).name();
    }

    public static int packCount() {
        if (serverMode) return serverPacks.size();
        return localPacks.size();
    }

    // ════════════════════════════════════════
    //  렌더링
    // ════════════════════════════════════════

    /** 텍스트의 렌더링 가로 폭 계산 */
    public static int measureWidth(String text) {
        return measureWidth(text, defaultCellWidth);
    }

    public static int measureWidth(String text, int cellW) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            if (CHAR_MAP.indexOf(text.charAt(i)) >= 0) {
                w += cellW + 1;
            }
        }
        return Math.max(0, w - 1);
    }

    /** 기본 (내 스킨) 으로 그리기 */
    public static void draw(GuiGraphics g, String text, float x, float y, float alpha,
                            boolean crit, String damageType) {
        drawWithSkinId(g, text, x, y, alpha, crit, damageType, mySelectedSkin);
    }

    /** 특정 skinId 로 그리기 */
    public static void drawWithSkinId(GuiGraphics g, String text, float x, float y, float alpha,
                                       boolean crit, String damageType, String skinId) {
        if (text == null || text.isEmpty()) return;
        int alphaInt = Math.clamp((int) (alpha * 255), 0, 255);
        if (alphaInt <= 4) return;

        ResourceLocation tex = null;
        int cellW = defaultCellWidth;
        int cellH = defaultCellHeight;

        if (serverMode) {
            SkinPackInfo pack = findServerPack(skinId);
            if (pack == null) return;
            tex = pack.resolve(crit, damageType);
            cellW = pack.cellWidth;
            cellH = pack.cellHeight;
        } else {
            if (localPacks.isEmpty()) return;
            tex = localPacks.get(localCurrentIndex).resolve(crit, damageType);
        }

        if (tex == null) return;
        int texW = cellW * CHAR_COUNT;
        int texH = cellH;

        float curX = x;
        for (int i = 0; i < text.length(); i++) {
            int idx = CHAR_MAP.indexOf(text.charAt(i));
            if (idx < 0) continue;
            g.blit(RenderType::guiTextured, tex,
                    (int) curX, (int) y,
                    idx * cellW, 0,
                    cellW, cellH,
                    texW, texH);
            curX += cellW + 1;
        }
    }

    /** 그림자 포함 그리기 (기본 = 내 스킨) */
    public static void drawWithShadow(GuiGraphics g, String text, float x, float y, float alpha,
                                       boolean crit, String damageType) {
        drawWithShadow(g, text, x, y, alpha, crit, damageType, mySelectedSkin);
    }

    /** 그림자 포함 그리기 (특정 skinId) */
    public static void drawWithShadow(GuiGraphics g, String text, float x, float y, float alpha,
                                       boolean crit, String damageType, String skinId) {
        drawWithSkinId(g, text, x + 1, y + 1, alpha * 0.3f, crit, damageType, skinId);
        drawWithSkinId(g, text, x, y, alpha, crit, damageType, skinId);
    }

    /** 특정 skinId 의 셀 크기 */
    public static int getCellWidthForSkin(String skinId) {
        if (serverMode) {
            SkinPackInfo pack = findServerPack(skinId);
            return pack != null ? pack.cellWidth : defaultCellWidth;
        }
        return defaultCellWidth;
    }

    public static int getCellHeightForSkin(String skinId) {
        if (serverMode) {
            SkinPackInfo pack = findServerPack(skinId);
            return pack != null ? pack.cellHeight : defaultCellHeight;
        }
        return defaultCellHeight;
    }

    // ════════════════════════════════════════
    //  유틸
    // ════════════════════════════════════════

    /** 플레이어 UUID 에 해당하는 skinId 조회 */
    public static String getSkinForPlayer(UUID playerUuid) {
        String skin = playerSkinMap.get(playerUuid);
        return skin != null ? skin : "none";
    }

    private static SkinPackInfo findServerPack(String skinId) {
        if (skinId == null || "none".equals(skinId)) return null;
        for (SkinPackInfo pack : serverPacks) {
            if (pack.id.equals(skinId)) return pack;
        }
        return null;
    }

    /** 전환 메시지 표시 */
    public static void showSwitchMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String name = "none".equals(mySelectedSkin) ? "텍스트" : mySelectedSkin;
            if (serverMode) {
                SkinPackInfo pack = findServerPack(mySelectedSkin);
                if (pack != null) name = pack.displayName;
            }
            mc.player.displayClientMessage(
                    Component.literal("§e데미지 스킨: §f" + name),
                    true
            );
        }
    }

    /** 연결 해제 시 초기화 */
    public static void clearServerData() {
        serverMode = false;
        serverPacks.clear();
        ownedSkins.clear();
        mySelectedSkin = "none";
        playerSkinMap.clear();
    }
}
