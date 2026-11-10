package ravens4.module.impl.player;

import ravens4.event.ClientRotationEvent;
import ravens4.event.PreUpdateEvent;
import ravens4.helper.RotationHelper;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;
import ravens4.utility.BlockUtils;
import ravens4.utility.RotationUtils;
import ravens4.utility.Utils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * While you are falling, builds a landing ladder so the fall does no damage.
 * With a wall beside the landing spot it just puts a ladder on that wall. Without one it places a
 * block next to the landing spot, then a ladder on that block, so you land on the ladder.
 * Needs a block and a ladder in the hotbar.
 */
public class AutoLadder extends Module {
    private final SliderSetting minimumFallDistance;
    private final ButtonSetting onlyWhileHoldingLadder;

    // slot to return to once we are done, -1 = we have not switched
    private int previousSlot = -1;
    // two step mode: after placing the helper block, the ladder goes on it next
    private boolean ladderNext;
    private BlockPos pendingBeside;
    private EnumFacing pendingDirection;
    private long placedAt;

    // result of the last aim pass, consumed by onPreUpdate
    private boolean placeQueued;
    private int queuedSlot = -1;
    private boolean queuedIsBlock;
    private BlockPos queuedPos;
    private EnumFacing queuedSide;
    private Vec3 queuedHitVec;
    private BlockPos queuedBeside;
    private EnumFacing queuedDirection;

    public AutoLadder() {
        super("AutoLadder", category.player);
        this.registerSetting(minimumFallDistance = new SliderSetting("Minimum fall distance", " blocks", 4, 3, 20, 1));
        this.registerSetting(onlyWhileHoldingLadder = new ButtonSetting("Only while holding ladder", false));
    }

    @Override
    public void onDisable() {
        reset();
    }

    // Plans the next placement and aims at it. Safe to run more than once per tick: it only reads
    // the world and records what to place, the state only changes in onPreUpdate once a block or
    // ladder has really been placed.
    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        placeQueued = false;
        if (!this.isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying || mc.thePlayer.capabilities.isCreativeMode
                || isOtherBuilderActive()) {
            reset();
            return;
        }
        if (mc.thePlayer.onGround || mc.thePlayer.isOnLadder() || mc.thePlayer.fallDistance < 1.5f) {
            placedAt = 0L; // landed: the one second cooldown is over
            reset();
            return;
        }
        if (Math.abs(System.currentTimeMillis() - placedAt) < 1000L) {
            reset();
            return;
        }

        int blockSlot = -1;
        int ladderSlot = -1;
        int most = 0;
        for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s == null || !(s.getItem() instanceof ItemBlock)) {
                continue;
            }
            ItemBlock itemBlock = (ItemBlock) s.getItem();
            if (itemBlock.getBlock() == Blocks.ladder) {
                ladderSlot = i;
            } else if (Utils.canBePlaced(itemBlock) && s.stackSize > most) {
                most = s.stackSize;
                blockSlot = i;
            }
        }
        if (blockSlot == -1 || ladderSlot == -1) {
            reset();
            return;
        }
        if (onlyWhileHoldingLadder.isToggled() && !ladderNext && previousSlot == -1
                && mc.thePlayer.inventory.currentItem != ladderSlot) {
            return;
        }

        // is there ground ahead and below to land on?
        MovingObjectPosition ground = RotationUtils.rayCastBlock(7.0, mc.thePlayer.rotationYaw, mc.thePlayer.isSprinting() ? 80.0f : 90.0f);
        if (ground == null || ground.sideHit != EnumFacing.UP) {
            reset();
            return;
        }

        int slot;
        boolean isBlock = false;
        BlockPos pos = null;
        EnumFacing side = null;
        BlockPos beside = null;
        EnumFacing direction = null;

        if (ladderNext) {
            if (pendingBeside == null || pendingDirection == null) {
                reset();
                return;
            }
            slot = ladderSlot;
            pos = pendingBeside.up();
            side = pendingDirection.getOpposite();
            if (!BlockUtils.replaceable(pos.offset(side))) {
                reset();
                return;
            }
        } else {
            BlockPos landing = ground.getBlockPos();
            if (!BlockUtils.replaceable(landing.up())) {
                reset();
                return;
            }
            slot = blockSlot;
            EnumFacing facing = mc.thePlayer.getHorizontalFacing();
            EnumFacing[] order = new EnumFacing[]{facing.rotateY(), facing.rotateYCCW(), facing, facing.getOpposite()};
            // 1) a solid wall next to the landing spot: put the ladder straight on it
            for (EnumFacing f : order) {
                BlockPos wall = landing.offset(f).up();
                if (BlockUtils.replaceable(wall) || !BlockUtils.getBlock(wall).isFullBlock()) {
                    continue;
                }
                slot = ladderSlot;
                pos = wall;
                side = f.getOpposite();
                break;
            }
            // 2) no wall: place a block beside the landing spot first, the ladder goes on it next
            if (pos == null) {
                double x = mc.thePlayer.posX;
                double z = mc.thePlayer.posZ;
                double m = 0.415;
                double m1 = m + 1.0;
                for (EnumFacing f : order) {
                    BlockPos next = landing.offset(f);
                    BlockPos above = next.up();
                    if (x > above.getX() - m && x < above.getX() + m1 && z > above.getZ() - m && z < above.getZ() + m1) {
                        continue; // we are standing over it
                    }
                    if (BlockUtils.replaceable(next) || !BlockUtils.replaceable(above)) {
                        continue;
                    }
                    pos = next;
                    side = EnumFacing.UP;
                    isBlock = true;
                    beside = next;
                    direction = f;
                    break;
                }
            }
        }
        if (pos == null || side == null) {
            reset();
            return;
        }
        if (mc.thePlayer.fallDistance < minFall()) {
            return;
        }

        // aim at the face we want to place on and make sure the look really hits it
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 c = BlockUtils.getFaceCenter(pos, side);
        float[] rot = RotationUtils.getRotationsFromEye(eye, c.xCoord, c.yCoord, c.zCoord);
        MovingObjectPosition mop = RotationUtils.rayCastBlock(mc.playerController.getBlockReachDistance(), rot[0], rot[1]);
        RotationHelper.get().forceMovementFix = true;
        e.setYaw(rot[0]);
        e.setPitch(rot[1]);
        if (mop != null && pos.equals(mop.getBlockPos()) && side == mop.sideHit) {
            placeQueued = true;
            queuedSlot = slot;
            queuedIsBlock = isBlock;
            queuedPos = pos;
            queuedSide = side;
            queuedHitVec = mop.hitVec;
            queuedBeside = beside;
            queuedDirection = direction;
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!placeQueued || !this.isEnabled() || !Utils.nullCheck()) {
            return;
        }
        placeQueued = false;
        if (queuedSlot < 0 || queuedPos == null || queuedSide == null || queuedHitVec == null) {
            return;
        }
        if (previousSlot == -1) {
            previousSlot = mc.thePlayer.inventory.currentItem;
        }
        mc.thePlayer.inventory.currentItem = queuedSlot;
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), queuedPos, queuedSide, queuedHitVec)) {
            mc.thePlayer.swingItem();
            if (queuedIsBlock) {
                ladderNext = true;
                pendingBeside = queuedBeside;
                pendingDirection = queuedDirection;
            } else {
                ladderNext = false;
                placedAt = System.currentTimeMillis();
            }
        }
    }

    private boolean isOtherBuilderActive() {
        Module scaffold = Module.getModule(Scaffold.class);
        return (scaffold != null && scaffold.isEnabled())
                || (ModuleManager.blockIn != null && ModuleManager.blockIn.isEnabled());
    }

    private double minFall() {
        PotionEffect jump = mc.thePlayer.getActivePotionEffect(Potion.jump);
        return minimumFallDistance.getInput() - 1.5 + (jump == null ? 0 : jump.getAmplifier() + 1);
    }

    private void reset() {
        if (previousSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        ladderNext = false;
        pendingBeside = null;
        pendingDirection = null;
        placeQueued = false;
    }
}
