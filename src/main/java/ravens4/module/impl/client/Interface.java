package ravens4.module.impl.client;

import ravens4.bus.EventPriority;
import ravens4.bus.GuiScreenEvent;
import ravens4.bus.MinecraftForge;
import ravens4.bus.MouseEvent;
import ravens4.bus.RenderWorldLastEvent;
import ravens4.bus.SubscribeEvent;
import ravens4.bus.TickEvent;
import ravens4.event.AttackEvent;
import ravens4.event.GuiUpdateEvent;
import ravens4.event.KeyPressEvent;
import ravens4.event.PreUpdateEvent;
import ravens4.script.ScriptDefaults;
import ravens4.script.model.Entity;
import ravens4.utility.Utils;
import net.minecraft.client.Minecraft;
import ravens4.mixin.impl.accessor.IAccessorEntityRenderer;
import ravens4.mixin.impl.accessor.IAccessorMinecraft;

/**
 * Runs the Rise interface (RClickGui, the DLayout3 HUD, notifications...). It is not a module: it starts once with
 * the client and forwards the game events to the interface code in {@link ravens4.module.impl.client.rise.Interface}.
 */
public final class Interface {
    private static final Interface INSTANCE = new Interface();

    private final ravens4.module.impl.client.rise.Interface script = new ravens4.module.impl.client.rise.Interface();
    private final Events events = new Events();
    private boolean started = false;

    private Interface() {
    }

    /** Starts the interface for good; called once after ModuleManager.register() (see Raven init). */
    public static void start() {
        INSTANCE.init();
    }

    private void init() {
        if (this.started) {
            return;
        }
        this.started = true;
        try {
            ScriptDefaults.reloadModules();
            script.onLoad();
            MinecraftForge.EVENT_BUS.register(this.events);
            script.onEnable();
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public class Events {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onPreUpdate(PreUpdateEvent e) {
            script.onPreUpdate();
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onAttack(AttackEvent e) {
            if (e.isCanceled()) {
                return;
            }
            if (!script.onAttackEntity(Entity.convert(e.target), Entity.convert(e.attacker))) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onRenderWorldLast(RenderWorldLastEvent e) {
            if (!Utils.nullCheck()) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 0);
            script.onRenderWorld(e.partialTicks);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onRenderTick(TickEvent.RenderTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
                return;
            }
            script.onRenderTick(e.renderTickTime);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onGuiUpdate(GuiUpdateEvent e) {
            if (e.guiScreen == null) {
                return;
            }
            script.onGuiUpdate(e.guiScreen.getClass().getSimpleName(), e.opened);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onKeyTyped(KeyPressEvent e) {
            if (e.isCanceled()) {
                return;
            }
            if (!script.onKeyPress(e.typedChar, e.keyCode)) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onMouse(MouseEvent e) {
            ravens4.module.impl.client.rise.Interface.feedWheel(e.dwheel);
            if (!script.onMouse(e.button, e.buttonstate, 0, 0)) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onScreenRender(GuiScreenEvent.DrawScreenEvent.Post e) {
            script.onScreenRender(e.gui == null ? "" : e.gui.getClass().getSimpleName(), e.mouseX, e.mouseY, e.renderPartialTicks);
        }
    }
}
