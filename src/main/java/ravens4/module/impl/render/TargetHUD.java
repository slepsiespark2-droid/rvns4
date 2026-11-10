package ravens4.module.impl.render;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import ravens4.mixin.impl.accessor.IAccessorEntityRenderer;
import ravens4.module.Module;
import ravens4.module.impl.combat.KillAura;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.RenderUtils;
import ravens4.utility.Utils;
import ravens4.utility.shader.RoundedUtils;

import java.awt.Color;
import java.io.IOException;

/**
 * Shows the current KillAura target: avatar, name, held item + armor, an animated health bar with a
 * "ghost" trail for recently lost health, and your health difference to the target.
 * Native port of the TargetHUD script.
 */
public class TargetHUD extends Module {
    private static final String[] COLOR_NAMES = new String[]{
            "Cherry", "Cotton Candy", "Flare", "Flower", "Gold", "Grayscale",
            "Royal", "Sky", "Vine", "Sunset", "Purple", "Red"
    };
    /** {bar start, bar end} per colour mode. */
    private static final Color[][] COLOR_MODES = new Color[][]{
            {new Color(255, 200, 200), new Color(243, 58, 106)},
            {new Color(99, 249, 255), new Color(255, 104, 204)},
            {new Color(231, 39, 24), new Color(245, 173, 49)},
            {new Color(215, 166, 231), new Color(211, 90, 232)},
            {new Color(255, 215, 0), new Color(240, 159, 0)},
            {new Color(240, 240, 240), new Color(110, 110, 110)},
            {new Color(125, 204, 241), new Color(30, 71, 170)},
            {new Color(160, 230, 225), new Color(15, 190, 220)},
            {new Color(17, 192, 45), new Color(201, 234, 198)},
            {new Color(234, 64, 24), new Color(161, 228, 49)},
            {new Color(143, 0, 91), new Color(0, 70, 255)},
            {new Color(255, 0, 0), new Color(170, 20, 20)}
    };

    private static final float DEFAULT_POS_X = 250.0f;
    private static final float DEFAULT_POS_Y = 220.0f;

    private static final float HUD_HEIGHT = 43.0f;
    private static final float MIN_WIDTH = 95.0f;
    private static final float AVATAR_EXTRA_WIDTH = 32.0f;
    private static final float CORNER_RADIUS = 8.5f;
    private static final float AVATAR_SIZE = 23.0f;
    private static final float NAME_SCALE = 1.2f;
    private static final float ITEM_SCALE = 0.9f;
    private static final float ITEM_SPACING = 15.0f;
    private static final int ITEM_SLOTS = 5; // held item + 4 armor pieces
    private static final float BAR_TOP = 31.0f;
    private static final float BAR_HEIGHT = 8.0f;
    private static final float BAR_RADIUS = 4.0f;
    private static final String HEART = "\u00a7c\u2764";

    private static final long FADE_OUT_MS = 350L;
    private static final long TICK_MS = 50L;
    private static final float HEALTH_SMOOTHING = 0.07f; // fraction per frame at 60 fps
    private static final float GHOST_SMOOTHING = 0.03f;

    private final SliderSetting backgroundAlpha;
    private final SliderSetting ghostBarDuration;
    private final SliderSetting ghostBarAlpha;
    private final SliderSetting colorMode;
    private final SliderSetting damageAlpha;
    private final SliderSetting damageEffectDuration;
    private final ButtonSetting showAvatar;
    private final ButtonSetting followPlayer;

    private float posX = DEFAULT_POS_X;
    private float posY = DEFAULT_POS_Y;

    // animation state
    private EntityLivingBase shownTarget;
    private long lastSeenMs;
    private long lastFrameNanos;
    private float lastHealth = -1.0f;
    private float fillRatio;
    private float ghostRatio;
    private long ghostUntilMs;
    private long damageUntilMs;
    private long damageDurationMs = 1L;

    // follow-player state
    private float followX = DEFAULT_POS_X;
    private float followY = DEFAULT_POS_Y;
    private RenderUtils.ProjectionContext projectionContext;
    private final double[] projectedPoint = new double[3];

    public TargetHUD() {
        super("TargetHUD", category.render);
        this.registerSetting(new DescriptionSetting("Only works with KillAura."));
        this.registerSetting(showAvatar = new ButtonSetting("Show avatar", true));
        this.registerSetting(followPlayer = new ButtonSetting("Follow player", false));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        this.registerSetting(colorMode = new SliderSetting("Color mode", 0, COLOR_NAMES));
        this.registerSetting(backgroundAlpha = new SliderSetting("Background alpha", 180, 0, 255, 1));
        this.registerSetting(ghostBarDuration = new SliderSetting("Ghost bar duration", " ticks", 30, 0, 100, 1));
        this.registerSetting(ghostBarAlpha = new SliderSetting("Ghost bar alpha", 100, 0, 255, 1));
        this.registerSetting(damageAlpha = new SliderSetting("Damage alpha", 128, 0, 255, 1));
        this.registerSetting(damageEffectDuration = new SliderSetting("Damage effect duration", " ticks", 10, 0, 40, 1));
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        shownTarget = null;
        lastHealth = -1.0f;
        lastFrameNanos = 0L;
        ghostUntilMs = 0L;
        damageUntilMs = 0L;
    }

    // ============================== frame update ==============================

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!Utils.nullCheck() || (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat))) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        long nanos = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 1.0f / 60.0f : Math.min(0.1f, (nanos - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = nanos;

        EntityLivingBase current = KillAura.target;
        float fade = 1.0f;
        if (current != null) {
            if (current != shownTarget) {
                shownTarget = current;
                lastHealth = getHealth(current);
                fillRatio = getHealthRatio(current);
                ghostRatio = fillRatio;
                ghostUntilMs = 0L;
                damageUntilMs = 0L;
            }
            lastSeenMs = now;
        }
        else if (shownTarget != null) {
            long elapsed = now - lastSeenMs;
            if (elapsed >= FADE_OUT_MS) {
                shownTarget = null;
                return;
            }
            fade = 1.0f - elapsed / (float) FADE_OUT_MS;
        }
        else {
            return;
        }

        EntityLivingBase target = shownTarget;
        float health = getHealth(target);
        float ratio = getHealthRatio(target);

        if (lastHealth >= 0.0f && health < lastHealth - 0.001f) {
            long ghostMs = (long) ghostBarDuration.getInput() * TICK_MS;
            long damageMs = (long) damageEffectDuration.getInput() * TICK_MS;
            // the trail starts where the bar currently is (or stays at its higher value if it is still draining)
            ghostRatio = now < ghostUntilMs ? Math.max(ghostRatio, fillRatio) : fillRatio;
            ghostUntilMs = now + ghostMs;
            damageDurationMs = Math.max(1L, damageMs);
            damageUntilMs = now + damageMs;
        }
        lastHealth = health;

        fillRatio = approach(fillRatio, ratio, HEALTH_SMOOTHING, dt);
        if (now < ghostUntilMs) {
            ghostRatio = approach(ghostRatio, fillRatio, GHOST_SMOOTHING, dt);
        }
        else {
            ghostRatio = fillRatio;
        }

        float damageFlash = now < damageUntilMs ? clamp((damageUntilMs - now) / (float) damageDurationMs, 0.0f, 1.0f) : 0.0f;

        ScaledResolution resolution = new ScaledResolution(mc);
        float width = computeWidth(target);

        float x;
        float y;
        if (followPlayer.isToggled()) {
            updateFollowPosition(target, width, event.renderTickTime, resolution);
            x = followX;
            y = followY;
        }
        else {
            x = posX;
            y = posY;
        }
        x = clamp(x, 0.0f, Math.max(0.0f, resolution.getScaledWidth() - width));
        y = clamp(y, 0.0f, Math.max(0.0f, resolution.getScaledHeight() - HUD_HEIGHT));

        drawHud(target, x, y, width, fade, fillRatio, now < ghostUntilMs ? ghostRatio : fillRatio, damageFlash);
    }

    /** Projects the top of the target's head to the screen and pins the HUD there. */
    private void updateFollowPosition(EntityLivingBase target, float width, float partialTicks, ScaledResolution resolution) {
        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null) {
            return;
        }

        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks - renderManager.viewerPosY + target.height;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;

        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
        projectionContext = RenderUtils.captureProjectionContext(projectionContext, resolution.getScaleFactor());
        mc.entityRenderer.setupOverlayRendering();

        if (RenderUtils.projectTo2D(projectionContext, x, y, z, projectedPoint)) {
            double depth = projectedPoint[2];
            if (depth > 0.0D && depth < 1.0003684D) {
                followX = (float) projectedPoint[0] - width / 2.0f;
                followY = (float) projectedPoint[1] - 25.0f;
            }
        }
    }

    // ============================== drawing ==============================

    private float computeWidth(EntityLivingBase target) {
        FontRenderer font = mc.fontRendererObj;
        float nameWidth = font.getStringWidth(target.getDisplayName().getFormattedText()) * NAME_SCALE;
        float left = getContentLeft();
        float minimum = MIN_WIDTH + (showAvatar.isToggled() ? AVATAR_EXTRA_WIDTH : 0.0f);
        float contentWidth = left + Math.max(nameWidth, ITEM_SLOTS * ITEM_SPACING) + 6.0f;
        return Math.max(minimum, Math.max(contentWidth, getGutterWidth() + 46.0f));
    }

    private float getContentLeft() {
        return showAvatar.isToggled() ? 29.0f : 6.0f;
    }

    /** Space reserved at the right end of the bar row for "<health difference> <heart>". */
    private float getGutterWidth() {
        FontRenderer font = mc.fontRendererObj;
        return font.getStringWidth("-20.0") + 2.0f + font.getStringWidth(HEART) + 4.0f;
    }

    /**
     * @return {x, y, width, height} of the drawn HUD
     */
    private float[] drawHud(EntityLivingBase target, float x, float y, float width, float fade, float fill, float ghost, float damageFlash) {
        FontRenderer font = mc.fontRendererObj;
        boolean avatar = showAvatar.isToggled();
        float left = getContentLeft();

        Color[] colors = COLOR_MODES[Math.max(0, Math.min(COLOR_MODES.length - 1, (int) colorMode.getInput()))];

        // background
        RoundedUtils.drawRound(x, y, width, HUD_HEIGHT, CORNER_RADIUS, argb(30, 30, 30, (int) (backgroundAlpha.getInput() * fade)));

        // avatar + damage flash
        if (avatar) {
            drawFace(target, x + 3.0f, y + 3.0f, fade);
            if (damageFlash > 0.0f) {
                int flashAlpha = (int) (damageAlpha.getInput() * damageFlash * fade);
                if (flashAlpha > 0) {
                    RenderUtils.drawRect(x + 3.0f, y + 3.0f, x + 3.0f + AVATAR_SIZE, y + 3.0f + AVATAR_SIZE, argb(255, 0, 0, flashAlpha));
                }
            }
        }

        // name
        int textAlpha = Math.max(4, (int) (255 * fade));
        int textColor = argb(255, 255, 255, textAlpha);
        if (fade > 0.03f) {
            GlStateManager.enableBlend();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + left, y + 3.0f, 0.0f);
            GlStateManager.scale(NAME_SCALE, NAME_SCALE, 1.0f);
            font.drawString(target.getDisplayName().getFormattedText(), 0, 0, textColor, true);
            GlStateManager.popMatrix();
        }

        // held item + armor, drawn in fixed slots; items can't be alpha-faded so they drop out halfway through the fade
        if (fade > 0.5f) {
            float itemX = x + left + 2.0f;
            float itemY = y + 13.0f;
            for (int slot = 0; slot < ITEM_SLOTS; slot++) {
                // equipment slot 0 is the held item, 4 is the helmet down to 1 for the boots
                ItemStack stack = target.getEquipmentInSlot(slot == 0 ? 0 : 5 - slot);
                if (stack != null) {
                    drawItem(stack, itemX + slot * ITEM_SPACING, itemY, ITEM_SCALE);
                }
            }
        }

        // health bar
        float barLeft = x + 3.0f;
        float barRight = x + width - 3.0f - getGutterWidth();
        float barWidth = Math.max(1.0f, barRight - barLeft);
        float barTop = y + BAR_TOP;
        float barBottom = barTop + BAR_HEIGHT;

        RoundedUtils.drawRound(barLeft, barTop, barWidth, BAR_HEIGHT, BAR_RADIUS, argb(50, 50, 50, (int) (255 * fade)));

        float fillWidth = barWidth * clamp(fill, 0.0f, 1.0f);
        float ghostWidth = barWidth * clamp(ghost, 0.0f, 1.0f);

        if (ghostWidth > fillWidth + 0.5f) {
            int a = (int) (ghostBarAlpha.getInput() * fade);
            if (a > 0) {
                RenderUtils.drawRoundedGradientRect(barLeft, barTop, barLeft + ghostWidth, barBottom, BAR_RADIUS,
                        withAlpha(colors[0], a), withAlpha(colors[0], a), withAlpha(colors[1], a), withAlpha(colors[1], a));
            }
        }
        if (fillWidth >= 1.0f) {
            int a = (int) (255 * fade);
            RenderUtils.drawRoundedGradientRect(barLeft, barTop, barLeft + fillWidth, barBottom, BAR_RADIUS,
                    withAlpha(colors[0], a), withAlpha(colors[0], a), withAlpha(colors[1], a), withAlpha(colors[1], a));
        }

        // health difference (you - target) and heart
        if (fade > 0.03f && mc.thePlayer != null) {
            float difference = getHealth(mc.thePlayer) - getHealth(target);
            String differenceText = String.format("%.1f", difference);
            float textX = barRight + 4.0f;
            GlStateManager.enableBlend();
            font.drawString(differenceText, textX, barTop, textColor, true);
            font.drawString(HEART, textX + font.getStringWidth(differenceText) + 2.0f, barTop, textColor, true);
        }

        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        return new float[]{x, y, width, HUD_HEIGHT};
    }

    private void drawFace(EntityLivingBase target, float x, float y, float fade) {
        int size = (int) AVATAR_SIZE;
        int ix = Math.round(x);
        int iy = Math.round(y);

        if (target instanceof AbstractClientPlayer) {
            ResourceLocation skin = ((AbstractClientPlayer) target).getLocationSkin();
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(1.0f, 1.0f, 1.0f, fade);
            Gui.drawScaledCustomSizeModalRect(ix, iy, 8.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f); // face
            Gui.drawScaledCustomSizeModalRect(ix, iy, 40.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f); // hat layer
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
        else {
            // no skin for mobs: neutral placeholder
            RenderUtils.drawRect(ix, iy, ix + size, iy + size, argb(60, 60, 60, (int) (255 * fade)));
        }
    }

    private void drawItem(ItemStack stack, float x, float y, float scale) {
        GlStateManager.pushMatrix();
        RenderUtils.prepareGuiItemRenderState();
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.scale(scale, scale, scale);
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, Math.round(x / scale), Math.round(y / scale));
        RenderHelper.disableStandardItemLighting();
        RenderUtils.prepareGuiTextureRenderState();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // ============================== helpers ==============================

    /**
     * Servers like Hypixel don't always sync other players' health to the client (it then sits at the vanilla
     * default of 1.0 and never changes), but they show it as hearts under the nametag via a scoreboard objective.
     * That value is preferred when it exists, otherwise the entity's own health is used.
     */
    private static float getHealth(EntityLivingBase entity) {
        if (entity.isDead) {
            return 0.0f;
        }
        if (entity instanceof EntityPlayer) {
            float scoreboardHealth = getScoreboardHealth((EntityPlayer) entity);
            if (scoreboardHealth >= 0.0f) {
                return scoreboardHealth;
            }
        }
        return entity.getHealth();
    }

    /** @return the health shown under the player's nametag, or -1 if there is no health objective for them */
    private static float getScoreboardHealth(EntityPlayer player) {
        try {
            Scoreboard scoreboard = player.getWorldScoreboard();
            if (scoreboard == null) {
                return -1.0f;
            }
            ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2); // 2 = below name
            if (objective == null || !isHealthObjective(objective) || !scoreboard.entityHasObjective(player.getName(), objective)) {
                return -1.0f;
            }
            Score score = scoreboard.getValueFromObjective(player.getName(), objective);
            if (score == null || score.getScorePoints() < 0) {
                return -1.0f;
            }
            return score.getScorePoints();
        }
        catch (Exception exception) {
            return -1.0f;
        }
    }

    private static boolean isHealthObjective(ScoreObjective objective) {
        IScoreObjectiveCriteria criteria = objective.getCriteria();
        if (criteria != null && "health".equals(criteria.getName())) {
            return true;
        }
        String title = EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName());
        return title != null && (title.contains("\u2764") || title.toLowerCase().contains("health"));
    }

    private static float getHealthRatio(EntityLivingBase entity) {
        float health = getHealth(entity);
        // the scoreboard can report more than the entity's max health (e.g. absorption), so never exceed a full bar
        return clamp(health / Math.max(0.01f, Math.max(entity.getMaxHealth(), health)), 0.0f, 1.0f);
    }

    /** Frame-rate independent version of {@code current += (target - current) * speed} (speed is per frame at 60 fps). */
    private static float approach(float current, float target, float speed, float dt) {
        float k = 1.0f - (float) Math.pow(1.0 - speed, dt * 60.0f);
        return current + (target - current) * k;
    }

    private static int argb(int r, int g, int b, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(Color color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color.getRGB() & 0x00FFFFFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ============================== position (saved in profiles) ==============================

    public float getPosX() {
        return posX;
    }

    public float getPosY() {
        return posY;
    }

    public void setPosition(float x, float y) {
        if (x == x) {
            posX = x;
        }
        if (y == y) {
            posY = y;
        }
    }

    private class EditScreen extends GuiScreen {
        private GuiButtonExt resetPosition;
        private float[] bounds;
        private boolean dragging;
        private int dragStartMouseX;
        private int dragStartMouseY;
        private float dragStartX;
        private float dragStartY;

        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, this.width, this.height, -1308622848);

            if (mc.thePlayer != null) {
                float width = computeWidth(mc.thePlayer);
                posX = clamp(posX, 0.0f, Math.max(0.0f, this.width - width));
                posY = clamp(posY, 0.0f, Math.max(0.0f, this.height - HUD_HEIGHT));
                float ratio = getHealthRatio(mc.thePlayer);
                this.bounds = drawHud(mc.thePlayer, posX, posY, width, 1.0f, ratio, ratio, 0.0f);
            }

            String message = followPlayer.isToggled()
                    ? "Follow player is on, so the HUD sticks to the target instead."
                    : "Edit the HUD position by dragging.";
            this.drawCenteredString(this.fontRendererObj, message, this.width / 2, this.height / 2 - 20, 0xFFFFFF);

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton != 0 || this.bounds == null) {
                return;
            }

            if (mouseX >= this.bounds[0] && mouseX <= this.bounds[0] + this.bounds[2]
                    && mouseY >= this.bounds[1] && mouseY <= this.bounds[1] + this.bounds[3]) {
                this.dragging = true;
                this.dragStartMouseX = mouseX;
                this.dragStartMouseY = mouseY;
                this.dragStartX = posX;
                this.dragStartY = posY;
            }
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            if (!this.dragging || clickedMouseButton != 0) {
                return;
            }

            posX = this.dragStartX + (mouseX - this.dragStartMouseX);
            posY = this.dragStartY + (mouseY - this.dragStartMouseY);
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button == this.resetPosition) {
                posX = DEFAULT_POS_X;
                posY = DEFAULT_POS_Y;
                this.dragging = false;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
