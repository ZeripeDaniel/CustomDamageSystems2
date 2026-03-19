package org.zeripe.angongui.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.zeripe.angongcommon.network.StatPayload;
import org.zeripe.angongui.client.network.NetworkHandler;
import org.zeripe.customdamagesystem.item.AccessoryInventory;
import org.zeripe.customdamagesystem.item.AccessoryMenu;

public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> {

    private static final ResourceLocation INVENTORY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    /* ── colour palette (matches StatScreen dark-blue theme) ─────────── */
    private static final int C_BG       = 0xF0101828;
    private static final int C_TITLE_BG = 0xFF0D1219;
    private static final int C_BORDER   = 0xFF2A4A6B;
    private static final int C_GLOW     = 0x222A4A6B;
    private static final int C_LABEL    = 0xFF8BAEC8;
    private static final int C_SLOT_BG  = 0xFF0D1219;
    private static final int C_SLOT_HI  = 0xFF3A5A7B;
    private static final int C_SLOT_LO  = 0xFF060C14;
    private static final int C_ACCENT   = 0xFF4A90D9;
    private static final int C_RING     = 0xFFFFD700;
    private static final int C_NECK     = 0xFF4ADFFF;
    private static final int C_EAR      = 0xFFE040FB;
    private static final int C_WEAPON   = 0xFFFF4444;

    /* ── panel geometry ─────────────────────────────────────────────── */
    private static final int PX = 82, PY = 6, PW = 92, PH = 76;
    private static final int SZ = 18;

    /* ── tab geometry ──────────────────────────────────────────────── */
    private static final int TAB_SZ = 22, TAB_GAP = 2;

    /* ── slot visual positions (relative to panel) ─────────────────── */
    // These match the slot x,y in AccessoryMenu minus leftPos+PX / topPos+PY offsets
    // Menu slots: R1(91,33), R2(91,54), N(120,33), E1(149,33), E2(149,54)
    // Panel starts at leftPos+82, topPos+6
    // So relative: R1(9,27), R2(9,48), N(38,27), E1(67,27), E2(67,48)
    private static final int[][] SLOT_POS = {
            {9,  27},   // Ring 1
            {9,  48},   // Ring 2
            {38, 27},   // Necklace
            {67, 27},   // Earring 1
            {67, 48},   // Earring 2
            {38, 48},   // Weapon
    };
    private static final int[] SLOT_ACCENT = {C_RING, C_RING, C_NECK, C_EAR, C_EAR, C_WEAPON};

    public AccessoryScreen(AccessoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 73;
    }

    @Override
    protected void init() {
        super.init();

        int tx = this.leftPos - TAB_SZ - 1;
        int ty = this.topPos + 4;

        addRenderableWidget(new IconTabButton(
                tx, ty, TAB_SZ,
                new ItemStack(Items.CHEST),
                Component.translatable("ui.customdamagesystem.tab.inventory"),
                false,
                () -> {
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.setScreen(new InventoryScreen(minecraft.player));
                    }
                }
        ));
        addRenderableWidget(new IconTabButton(
                tx, ty + TAB_SZ + TAB_GAP, TAB_SZ,
                new ItemStack(Items.TOTEM_OF_UNDYING),
                Component.translatable("ui.customdamagesystem.tab.accessory"),
                true,
                () -> {}
        ));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Draw vanilla inventory texture (bottom part: inventory + hotbar)
        g.blit(RenderType::guiTextured, INVENTORY_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        // Dark overlay on top area (where crafting grid was), covering y < 83
        int topAreaBottom = this.topPos + 83;
        g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, topAreaBottom, 0xFF101828);

        // ── Equip stat panel (left side) ──
        renderStatPanel(g);

        // ── Accessory panel ──
        int px = this.leftPos + PX;
        int py = this.topPos + PY;

        g.fill(px, py, px + PW, py + PH, C_BG);
        drawBorder(g, px, py, PW, PH);

        // title bar
        g.fill(px + 1, py + 1, px + PW - 1, py + 13, C_TITLE_BG);
        g.fill(px + 1, py + 13, px + PW - 1, py + 14, C_BORDER);
        g.fill(px + 1, py + 1, px + PW - 1, py + 2, C_GLOW);

        // accent diamond
        int diamX = px + 6, diamY = py + 5;
        g.fill(diamX + 1, diamY, diamX + 3, diamY + 1, C_ACCENT);
        g.fill(diamX, diamY + 1, diamX + 4, diamY + 2, C_ACCENT);
        g.fill(diamX + 1, diamY + 2, diamX + 3, diamY + 3, C_ACCENT);

        String title = I18n.get("ui.customdamagesystem.acc.title");
        int tw = FontUtil.width(this.font, title);
        FontUtil.draw(g, this.font, title, px + (PW - tw) / 2 + 3, py + 3, C_LABEL, false);

        // column labels (top row)
        int ly = py + 16;
        drawCentered(g, I18n.get("ui.customdamagesystem.acc.ring"),     px + 18, ly, C_RING);
        drawCentered(g, I18n.get("ui.customdamagesystem.acc.necklace"), px + 47, ly, C_NECK);
        drawCentered(g, I18n.get("ui.customdamagesystem.acc.earring"),  px + 76, ly, C_EAR);
        // weapon label (below weapon slot)
        drawCentered(g, I18n.get("ui.customdamagesystem.acc.weapon"), px + 47, py + 67, C_WEAPON);

        // slot backgrounds
        for (int i = 0; i < SLOT_POS.length; i++) {
            int sx = px + SLOT_POS[i][0];
            int sy = py + SLOT_POS[i][1];
            boolean hov = mouseX >= sx && mouseX < sx + SZ && mouseY >= sy && mouseY < sy + SZ;
            drawSlotBg(g, sx, sy, hov, SLOT_ACCENT[i]);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Draw inventory label in light color
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, C_LABEL, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    /**
     * 플러그인 서버(clientOnly 모드)에서는 서버로 컨테이너 클릭 패킷을 보내지 않고
     * 클라이언트에서만 슬롯 조작을 처리한다.
     */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int button, ClickType clickType) {
        if (this.menu.isClientOnly()) {
            // 서버 패킷 없이 로컬에서만 클릭 처리
            this.menu.clicked(slotId, button, clickType, this.minecraft.player);
            return;
        }
        super.slotClicked(slot, slotId, button, clickType);
    }

    @Override
    public void removed() {
        super.removed();
        // 플러그인 서버 (서버에 AccessoryMenu가 없는 경우) → 패킷으로 슬롯 데이터 전송
        if (this.menu.isClientOnly()) {
            // containerMenu를 원래 inventoryMenu로 복원
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.containerMenu = minecraft.player.inventoryMenu;
            }
            if (ClientPlayNetworking.canSend(StatPayload.TYPE)) {
                sendAccessoryUpdateToServer();
            }
        }
    }

    private void sendAccessoryUpdateToServer() {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "accessory_update");
        JsonArray slots = new JsonArray();
        for (int i = 0; i < AccessoryInventory.SIZE; i++) {
            ItemStack stack = this.menu.getSlot(i).getItem();
            if (stack.isEmpty()) {
                slots.add((String) null);
            } else {
                // registryId + itemId + cmd 전송 (서버가 full item 복원 가능하도록)
                JsonObject slotData = new JsonObject();
                slotData.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                // CustomModelData
                var cmdData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
                if (cmdData != null && !cmdData.floats().isEmpty()) {
                    slotData.addProperty("cmd", cmdData.floats().getFirst().intValue());
                }
                // registryId는 EquipmentStatConfig에서 찾기
                String regId = findRegistryId(stack);
                if (regId != null) slotData.addProperty("registryId", regId);
                slots.add(slotData);
            }
        }
        packet.add("slots", slots);
        ClientPlayNetworking.send(StatPayload.of(packet.toString()));
    }

    /** 아이템의 registryId를 찾기 — accessory_registry_sync 캐시 사용 */
    private String findRegistryId(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int cmd = 0;
        var cmdData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        if (cmdData != null && !cmdData.floats().isEmpty()) {
            cmd = cmdData.floats().getFirst().intValue();
        }
        return ClientAccessoryRegistryCache.findRegistryId(itemId, cmd);
    }

    private void renderStatPanel(GuiGraphics g) {
        ClientState.PlayerStats s = ClientState.get().getPlayerStats();

        int px = this.leftPos + 4;
        int py = this.topPos + 6;
        int pw = 74;
        int ph = 76;

        // panel bg + border
        g.fill(px, py, px + pw, py + ph, C_BG);
        drawBorder(g, px, py, pw, ph);

        // title bar
        g.fill(px + 1, py + 1, px + pw - 1, py + 13, C_TITLE_BG);
        g.fill(px + 1, py + 13, px + pw - 1, py + 14, C_BORDER);
        g.fill(px + 1, py + 1, px + pw - 1, py + 2, C_GLOW);

        String statTitle = I18n.get("ui.customdamagesystem.stat.base_stats");
        int stw = FontUtil.width(this.font, statTitle);
        FontUtil.draw(g, this.font, statTitle, px + (pw - stw) / 2, py + 3, C_LABEL, false);

        // stat rows
        int sy = py + 17;
        int lineH = 12;
        int labelX = px + 5;
        int valX = px + pw - 5;

        drawStatRow(g, I18n.get("ui.customdamagesystem.stat.strength"),     s.equipStrength(), 0xFFFF6B6B, labelX, valX, sy);
        sy += lineH;
        drawStatRow(g, I18n.get("ui.customdamagesystem.stat.agility"),      s.equipAgility(),  0xFF6BFF6B, labelX, valX, sy);
        sy += lineH;
        drawStatRow(g, I18n.get("ui.customdamagesystem.stat.intelligence"), s.equipIntelligence(), 0xFF6BB5FF, labelX, valX, sy);
        sy += lineH;
        drawStatRow(g, I18n.get("ui.customdamagesystem.stat.luck"),         s.equipLuck(),     0xFFFFD700, labelX, valX, sy);
    }

    private void drawStatRow(GuiGraphics g, String label, int value, int color, int labelX, int valRightX, int y) {
        FontUtil.draw(g, this.font, label, labelX, y, C_LABEL, false);
        String val = String.valueOf(value);
        int vw = FontUtil.width(this.font, val);
        FontUtil.draw(g, this.font, val, valRightX - vw, y, color, false);
    }

    /* ── drawing helpers ─────────────────────────────────────────────── */

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, C_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        g.fill(x, y, x + 1, y + h, C_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, C_BORDER);
    }

    private void drawSlotBg(GuiGraphics g, int x, int y, boolean hovered, int accent) {
        g.fill(x, y, x + SZ, y + 1, C_SLOT_HI);
        g.fill(x, y, x + 1, y + SZ, C_SLOT_HI);
        g.fill(x + SZ - 1, y + 1, x + SZ, y + SZ, C_SLOT_LO);
        g.fill(x + 1, y + SZ - 1, x + SZ, y + SZ, C_SLOT_LO);
        g.fill(x + 1, y + 1, x + SZ - 1, y + SZ - 1, C_SLOT_BG);
        g.fill(x + 1, y + 1, x + 3, y + 3, accent);
        if (hovered) {
            g.fill(x + 1, y + 1, x + SZ - 1, y + SZ - 1, 0x44FFFFFF);
        }
    }

    private void drawCentered(GuiGraphics g, String text, int cx, int y, int colour) {
        int w = FontUtil.width(this.font, text);
        FontUtil.draw(g, this.font, text, cx - w / 2, y, colour, false);
    }
}
