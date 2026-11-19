package ravens4.bus;

import net.minecraftforge.fml.common.eventhandler.EventPriority;

/**
 * The only place that still listens to Forge's own events. Each one is turned into the client's own event and
 * posted on the client's bus; if a handler cancels it (or changes it), that is passed back to Forge. Once the client
 * no longer runs on Forge, the hooks that call this are replaced by mixins and this class goes away.
 */
public final class ForgeBridge {
    private static final EventBus BUS = MinecraftForge.EVENT_BUS;

    private static TickEvent.Phase phase(net.minecraftforge.fml.common.gameevent.TickEvent.Phase p) {
        return p == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START ? TickEvent.Phase.START : TickEvent.Phase.END;
    }

    private static Side side(net.minecraftforge.fml.relauncher.Side s) {
        return s == net.minecraftforge.fml.relauncher.Side.SERVER ? Side.SERVER : Side.CLIENT;
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onClientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e) {
        if (BUS.hasListeners(TickEvent.ClientTickEvent.class)) {
            BUS.post(new TickEvent.ClientTickEvent(phase(e.phase)));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRenderTick(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent e) {
        if (BUS.hasListeners(TickEvent.RenderTickEvent.class)) {
            BUS.post(new TickEvent.RenderTickEvent(phase(e.phase), e.renderTickTime));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onPlayerTick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent e) {
        if (BUS.hasListeners(TickEvent.PlayerTickEvent.class)) {
            BUS.post(new TickEvent.PlayerTickEvent(phase(e.phase), side(e.side), e.player));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRenderWorldLast(net.minecraftforge.client.event.RenderWorldLastEvent e) {
        if (BUS.hasListeners(RenderWorldLastEvent.class)) {
            BUS.post(new RenderWorldLastEvent(e.context, e.partialTicks));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(net.minecraftforge.client.event.MouseEvent e) {
        if (BUS.hasListeners(MouseEvent.class)) {
            MouseEvent own = new MouseEvent(e.x, e.y, e.dx, e.dy, e.dwheel, e.button, e.buttonstate, e.nanoseconds);
            if (BUS.post(own)) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (BUS.hasListeners(EntityJoinWorldEvent.class)) {
            if (BUS.post(new EntityJoinWorldEvent(e.entity, e.world))) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientChat(net.minecraftforge.client.event.ClientChatReceivedEvent e) {
        if (BUS.hasListeners(ClientChatReceivedEvent.class)) {
            ClientChatReceivedEvent own = new ClientChatReceivedEvent(e.type, e.message);
            boolean canceled = BUS.post(own);
            e.message = own.message;
            if (canceled) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onWorldLoad(net.minecraftforge.event.world.WorldEvent.Load e) {
        if (BUS.hasListeners(WorldEvent.Load.class)) {
            BUS.post(new WorldEvent.Load(e.world));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload e) {
        if (BUS.hasListeners(WorldEvent.Unload.class)) {
            BUS.post(new WorldEvent.Unload(e.world));
        }
    }

    @SuppressWarnings("rawtypes")
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre e) {
        if (BUS.hasListeners(RenderLivingEvent.Pre.class)) {
            if (BUS.post(new RenderLivingEvent.Pre(e.entity, e.renderer))) {
                e.setCanceled(true);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRenderLivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post e) {
        if (BUS.hasListeners(RenderLivingEvent.Post.class)) {
            BUS.post(new RenderLivingEvent.Post(e.entity, e.renderer));
        }
    }

    @SuppressWarnings("rawtypes")
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderLivingSpecialsPre(net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre e) {
        if (BUS.hasListeners(RenderLivingEvent.Specials.Pre.class)) {
            if (BUS.post(new RenderLivingEvent.Specials.Pre(e.entity, e.renderer))) {
                e.setCanceled(true);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRenderLivingSpecialsPost(net.minecraftforge.client.event.RenderLivingEvent.Specials.Post e) {
        if (BUS.hasListeners(RenderLivingEvent.Specials.Post.class)) {
            BUS.post(new RenderLivingEvent.Specials.Post(e.entity, e.renderer));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGuiOpen(net.minecraftforge.client.event.GuiOpenEvent e) {
        if (BUS.hasListeners(GuiOpenEvent.class)) {
            GuiOpenEvent own = new GuiOpenEvent(e.gui);
            boolean canceled = BUS.post(own);
            e.gui = own.gui;
            if (canceled) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDrawScreenPost(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post e) {
        if (BUS.hasListeners(GuiScreenEvent.DrawScreenEvent.Post.class)) {
            BUS.post(new GuiScreenEvent.DrawScreenEvent.Post(e.gui, e.mouseX, e.mouseY, e.renderPartialTicks));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        if (BUS.hasListeners(AttackEntityEvent.class)) {
            if (BUS.post(new AttackEntityEvent(e.entityPlayer, e.target))) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onSetAttackTarget(net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent e) {
        if (BUS.hasListeners(LivingSetAttackTargetEvent.class)) {
            BUS.post(new LivingSetAttackTargetEvent(e.entityLiving, e.target));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent e) {
        if (BUS.hasListeners(LivingEvent.LivingUpdateEvent.class)) {
            if (BUS.post(new LivingEvent.LivingUpdateEvent(e.entityLiving))) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        if (BUS.hasListeners(RenderPlayerEvent.Pre.class)) {
            if (BUS.post(new RenderPlayerEvent.Pre(e.entityPlayer, e.renderer, e.partialRenderTick))) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRenderPlayerPost(net.minecraftforge.client.event.RenderPlayerEvent.Post e) {
        if (BUS.hasListeners(RenderPlayerEvent.Post.class)) {
            BUS.post(new RenderPlayerEvent.Post(e.entityPlayer, e.renderer, e.partialRenderTick));
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawBlockHighlight(net.minecraftforge.client.event.DrawBlockHighlightEvent e) {
        if (BUS.hasListeners(DrawBlockHighlightEvent.class)) {
            if (BUS.post(new DrawBlockHighlightEvent(e.context, e.player, e.target, e.subID, e.currentItem, e.partialTicks))) {
                e.setCanceled(true);
            }
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDisconnect(net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        if (BUS.hasListeners(FMLNetworkEvent.ClientDisconnectionFromServerEvent.class)) {
            BUS.post(new FMLNetworkEvent.ClientDisconnectionFromServerEvent(e.manager));
        }
    }
}
