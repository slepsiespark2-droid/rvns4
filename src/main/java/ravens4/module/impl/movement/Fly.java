package ravens4.module.impl.movement;

import ravens4.bus.EntityJoinWorldEvent;
import ravens4.bus.EventPriority;
import ravens4.bus.FMLNetworkEvent;
import ravens4.bus.MinecraftForge;
import ravens4.bus.SubscribeEvent;
import ravens4.bus.TickEvent;
import ravens4.event.PostMotionEvent;
import ravens4.event.PreMotionEvent;
import ravens4.event.PrePlayerInputEvent;
import ravens4.event.PreUpdateEvent;
import ravens4.event.ReceivePacketEvent;
import ravens4.event.SendPacketEvent;
import ravens4.module.Module;
import ravens4.module.impl.movement.fly.BoatFly;
import ravens4.module.setting.Setting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.script.ScriptDefaults;
import ravens4.script.model.Entity;
import ravens4.script.model.MovementInput;
import ravens4.script.model.PlayerState;
import ravens4.script.packet.clientbound.SPacket;
import ravens4.script.packet.serverbound.CPacket;
import ravens4.script.packet.serverbound.PacketHandler;
import ravens4.utility.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Fly with selectable modes. Current modes: Boat (see {@link BoatFly}).
 */
public class Fly extends Module {
    public static final String[] MODES = new String[]{"Boat"};

    private final SliderSetting mode;
    private final BoatFly boat = new BoatFly();
    private final List<Setting> boatSettings = new ArrayList<Setting>();
    private final Events events = new Events();
    private boolean eventsRegistered = false;
    private boolean boatLoaded = false;

    public Fly() {
        super("Fly", category.movement);
        this.registerSetting(this.mode = new SliderSetting("Mode", 0, MODES));
    }

    /** Registers the mode settings. Called once after ModuleManager.register() (see Raven init). */
    public void init() {
        if (this.boatLoaded) {
            return;
        }
        try {
            ScriptDefaults.reloadModules();
            int before = this.getSettings().size();
            boat.onLoad();
            for (int i = before; i < this.getSettings().size(); i++) {
                this.boatSettings.add(this.getSettings().get(i));
            }
            this.boatLoaded = true;
            this.guiUpdate();
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private boolean boatMode() {
        return (int) this.mode.getInput() == 0;
    }

    @Override
    public void guiUpdate() {
        boolean isBoat = boatMode();
        for (Setting setting : this.boatSettings) {
            setting.setVisible(isBoat, this);
        }
    }

    @Override
    public String getInfo() {
        return MODES[Math.max(0, Math.min(MODES.length - 1, (int) this.mode.getInput()))];
    }

    @Override
    public void onEnable() {
        if (!this.boatLoaded) {
            init();
        }
        if (!this.eventsRegistered) {
            MinecraftForge.EVENT_BUS.register(this.events);
            this.eventsRegistered = true;
        }
        if (boatMode()) {
            try {
                boat.onEnable();
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public void onDisable() {
        try {
            boat.onDisable();
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
        if (this.eventsRegistered) {
            MinecraftForge.EVENT_BUS.unregister(this.events);
            this.eventsRegistered = false;
        }
    }

    public class Events {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onPreUpdate(PreUpdateEvent e) {
            if (boatMode()) {
                boat.onPreUpdate();
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onPrePlayerInput(PrePlayerInputEvent e) {
            if (!boatMode()) {
                return;
            }
            MovementInput input = new MovementInput(e, (byte) 0);
            boat.onPrePlayerInput(input);
            if (e.isEquals(input)) {
                return;
            }
            e.setForward(input.forward);
            e.setSneak(input.sneak);
            e.setJump(input.jump);
            e.setStrafe(input.strafe);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onPreMotion(PreMotionEvent e) {
            if (!boatMode()) {
                return;
            }
            PlayerState state = new PlayerState(e, (byte) 0);
            boat.onPreMotion(state);
            if (e.isEquals(state)) {
                return;
            }
            if (e.getYaw() != state.yaw) {
                e.setYaw(state.yaw);
            }
            e.setPitch(state.pitch);
            e.setPosX(state.x);
            e.setPosY(state.y);
            e.setPosZ(state.z);
            e.setOnGround(state.onGround);
            e.setSprinting(state.isSprinting);
            e.setSneaking(state.isSneaking);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onPostMotion(PostMotionEvent e) {
            if (boatMode()) {
                boat.onPostMotion();
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onSendPacket(SendPacketEvent e) {
            if (!boatMode() || e.isCanceled() || e.getPacket() == null) {
                return;
            }
            if (e.getPacket().getClass().getSimpleName().startsWith("S")) {
                return;
            }
            CPacket packet = PacketHandler.convertServerBound(e.getPacket());
            if (packet != null && !boat.onPacketSent(packet)) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onReceivePacket(ReceivePacketEvent e) {
            if (!boatMode() || e.isCanceled() || e.getPacket() == null) {
                return;
            }
            SPacket packet = PacketHandler.convertClientBound(e.getPacket());
            if (packet != null && !boat.onPacketReceived(packet)) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onWorldJoin(EntityJoinWorldEvent e) {
            if (boatMode() && e.entity != null) {
                boat.onWorldJoin(Entity.convert(e.entity));
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onRenderTick(TickEvent.RenderTickEvent e) {
            if (!boatMode() || e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
                return;
            }
            boat.onRenderTick(e.renderTickTime);
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
            if (boatMode()) {
                boat.onDisconnect();
            }
        }
    }
}
