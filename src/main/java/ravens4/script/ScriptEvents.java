package ravens4.script;

import ravens4.Raven;
import ravens4.event.*;
import ravens4.mixin.impl.accessor.IAccessorEntityRenderer;
import ravens4.mixin.impl.accessor.IAccessorMinecraft;
import ravens4.module.Module;
import ravens4.script.model.Entity;
import ravens4.script.model.MovementInput;
import ravens4.script.model.PlayerState;
import ravens4.script.model.Vec3;
import ravens4.script.packet.clientbound.SPacket;
import ravens4.script.packet.serverbound.CPacket;
import ravens4.script.packet.serverbound.PacketHandler;
import ravens4.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public class ScriptEvents {
    public Module module;

    public ScriptEvents(Module module) {
        this.module = module;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || !Utils.nullCheck()) {
            return;
        }
        if (Utils.stripColor(e.message.getUnformattedText()).isEmpty()) {
            return;
        }
        if (Raven.scriptManager.invokeBoolean("onChat", module, e.message.getUnformattedText()) == 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSendPacket(SendPacketEvent e) {
        if (e.isCanceled() || e.getPacket() == null) {
            return;
        }
        if (e.getPacket().getClass().getSimpleName().startsWith("S")) {
            return;
        }
        CPacket packet = PacketHandler.convertServerBound(e.getPacket());
        if (packet != null && Raven.scriptManager.invokeBoolean("onPacketSent", module, packet) == 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDispatchPacket(DispatchPacketEvent e) {
        if (e.getPacket() == null) {
            return;
        }
        if (e.getPacket().getClass().getSimpleName().startsWith("S")) {
            return;
        }
        CPacket packet = PacketHandler.convertServerBound(e.getPacket());
        Raven.scriptManager.invoke("onDispatchPacket", module, packet);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.isCanceled() || e.getPacket() == null) {
            return;
        }
        SPacket packet = PacketHandler.convertClientBound(e.getPacket());
        if (packet != null && Raven.scriptManager.invokeBoolean("onPacketReceived", module, packet) == 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPreAttack(PreAttackEvent e) {
        if (e.isCanceled()) {
            return;
        }
        if (Raven.scriptManager.invokeBoolean("onPreAttack", module) == 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEvent e) {
        if (e.isCanceled()) {
            return;
        }
        Entity target = Entity.convert(e.target);
        Entity attacker = Entity.convert(e.attacker);
        if (Raven.scriptManager.invokeBoolean("onAttackEntity", module, target, attacker) == 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientRotations(ClientRotationEvent e) {
        Float[] rotations = Raven.scriptManager.invokeFloatArray("getRotations", module);
        if (rotations == null || rotations.length == 0 || rotations.length > 2) {
            return;
        }
        if (rotations[0] != null) {
            e.yaw = rotations[0];
            e.scriptRotations = true;
        }
        if (rotations.length == 2 && rotations[1] != null) {
            e.pitch = rotations[1];
            e.scriptRotations = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPrePlayerMovementInput(PrePlayerInputEvent e) {
        MovementInput input = new MovementInput(e, (byte) 0);
        Raven.scriptManager.invoke("onPrePlayerInput", module, input);
        if (e.isEquals(input)) {
            return;
        }
        e.setForward(input.forward);
        e.setSneak(input.sneak);
        e.setJump(input.jump);
        e.setStrafe(input.strafe);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyTyped(KeyPressEvent e) {
        if (e.isCanceled()) {
            return;
        }
        if (Raven.scriptManager.invokeBoolean("onKeyPress", module, e.typedChar, e.keyCode) == 0) {
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
        Raven.scriptManager.invoke("onRenderWorld", module, e.partialTicks);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent e) {
        Raven.scriptManager.invoke("onPreUpdate", module);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPostUpdate(PostUpdateEvent e) {
        Raven.scriptManager.invoke("onPostUpdate", module);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        Raven.scriptManager.invoke("onRenderTick", module, e.renderTickTime);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAntiCheatFlag(AntiCheatFlagEvent e) {
        Raven.scriptManager.invoke("onAntiCheatFlag", module, e.flag, Entity.convert(e.entity));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGuiUpdate(GuiUpdateEvent e) {
        if (e.guiScreen == null) {
            return;
        }
        Raven.scriptManager.invoke("onGuiUpdate", module, e.guiScreen.getClass().getSimpleName(), e.opened);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        Raven.scriptManager.invoke("onDisconnect", module);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPreMotion(PreMotionEvent e) {
        PlayerState playerState = new PlayerState(e, (byte) 0);
        Raven.scriptManager.invoke("onPreMotion", module, playerState);
        if (e.isEquals(playerState)) {
            return;
        }
        if (e.getYaw() != playerState.yaw) {
            e.setYaw(playerState.yaw);
        }
        e.setPitch(playerState.pitch);
        e.setPosX(playerState.x);
        e.setPosY(playerState.y);
        e.setPosZ(playerState.z);
        e.setOnGround(playerState.onGround);
        e.setSprinting(playerState.isSprinting);
        e.setSneaking(playerState.isSneaking);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        Raven.scriptManager.invoke("onPrePlayerInteract", module);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == null) {
            return;
        }
        Raven.scriptManager.invoke("onWorldJoin", module, Entity.convert(e.entity));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPostInput(PostPlayerInputEvent e) {
        Raven.scriptManager.invoke("onPostPlayerInput", module);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPostMotion(PostMotionEvent e) {
        Raven.scriptManager.invoke("onPostMotion", module);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (Raven.scriptManager.invokeBoolean("onMouse", module, e.button, e.buttonstate) == 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        Raven.scriptManager.invoke("onPlayerMove", module, new Vec3(e.x, e.y, e.z));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPreRenderModel(RenderLivingEvent.Pre e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (Raven.scriptManager.invokeBoolean("onPreRenderModel", module, Entity.convert(e.entity)) == 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPostRenderModel(RenderLivingEvent.Post e) {
        if (!Utils.nullCheck()) {
            return;
        }
        Raven.scriptManager.invoke("onPostRenderModel", module, Entity.convert(e.entity));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onScreenRender(GuiScreenEvent.DrawScreenEvent.Post e) {
        Raven.scriptManager.invoke("onScreenRender", module,
                e.gui == null ? "" : e.gui.getClass().getSimpleName(),
                e.mouseX, e.mouseY, e.renderPartialTicks);
    }

}