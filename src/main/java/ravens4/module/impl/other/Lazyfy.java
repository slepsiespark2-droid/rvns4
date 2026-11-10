package ravens4.module.impl.other;

import ravens4.event.ReceivePacketEvent;
import ravens4.event.SendPacketEvent;
import ravens4.module.Module;
import ravens4.module.ModuleManager;
import ravens4.module.impl.player.AntiBot;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.GroupSetting;
import ravens4.utility.BlockUtils;
import ravens4.utility.RenderUtils;
import ravens4.utility.Utils;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockObsidian;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Lazyfy extends Module {
    private static final String UPGRADE_CHAT_MARKER = "Team Upgrade";
    private static final String UPGRADE_CHAT_SEPARATOR = " purchased ";
    private static final int LEGGINGS_SLOT = 1;

    private static final Map<Enchantment, String> ARMOR_ENCHANT_NAMES = new LinkedHashMap<>();
    static {
        ARMOR_ENCHANT_NAMES.put(Enchantment.protection, "Protection");
        ARMOR_ENCHANT_NAMES.put(Enchantment.featherFalling, "Feather Falling");
    }

    // Bed Wars always keeps the helmet + chestplate as dyed leather in the team color, even
    // after boots/leggings get upgraded to iron/chain/diamond via Reinforced Armor. That makes
    // helmet+chestplate color a more reliable team indicator than the scoreboard team lookup,
    // which can fail to resolve (renamed players, delayed scoreboard sync, etc).
    private static final Map<EnumChatFormatting, Integer> TEAM_ARMOR_COLORS = new LinkedHashMap<>();
    static {
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.RED, 0xB02E26);
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.BLUE, 0x2E4A9E);
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.GREEN, 0x5D7C15);
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.YELLOW, 0xF6C858);
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.AQUA, 0x2AA9B8);
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.WHITE, 0xF3F3F3);
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.LIGHT_PURPLE, 0xE993B5);
        TEAM_ARMOR_COLORS.put(EnumChatFormatting.DARK_GRAY, 0x3B3B3B);
    }

    private ButtonSetting fireball;
    private ButtonSetting enderPearl;
    private ButtonSetting obsidian;
    private ButtonSetting shouldPing;

    private GroupSetting upgradesAlertGroup;
    private ButtonSetting upgradesAlert;
    private ButtonSetting upgradesPingSound;

    private GroupSetting armorAlertsGroup;
    private ButtonSetting armorAlerts;
    private ButtonSetting armorAlertsPingSound;
    private ButtonSetting armorAlertsChain;
    private ButtonSetting armorAlertsIron;
    private ButtonSetting armorAlertsDiamond;

    private Map<String, String> lastHeldMap = new ConcurrentHashMap<>();
    private Map<BlockPos, Long> obsidianPos = new HashMap<>(); // blockPos, time received
    public List<SkyWars.SpawnEggInfo> entitySpawnQueue = new ArrayList<>();
    public List<Integer> spawnedMobs = new ArrayList<>(); // entity id

    private int alertTickCounter = 0;
    private final Map<String, Set<String>> playerUpgradeMap = new HashMap<>();
    private final Map<String, Set<String>> teamUpgradeMap = new HashMap<>();
    private final Map<String, String> currentPlayerArmorMap = new HashMap<>();
    private boolean wasInBedwarsGame = false;

    private int obsidianColor = new Color(106, 13, 173).getRGB();

    public Lazyfy() {
        super("Lazyfy", category.other);
        this.registerSetting(new DescriptionSetting("Game alerts"));
        this.registerSetting(fireball = new ButtonSetting("Fireball", false));
        this.registerSetting(obsidian = new ButtonSetting("Obsidian", true));
        this.registerSetting(enderPearl = new ButtonSetting("Ender pearl", true));
        this.registerSetting(shouldPing = new ButtonSetting("Should ping", true));

        this.registerSetting(upgradesAlertGroup = new GroupSetting("Upgrades alert"));
        this.registerSetting(upgradesAlert = new ButtonSetting(upgradesAlertGroup, "Enabled", true));
        this.registerSetting(upgradesPingSound = new ButtonSetting(upgradesAlertGroup, "Ping sound", true));

        this.registerSetting(armorAlertsGroup = new GroupSetting("Armor alerts"));
        this.registerSetting(armorAlerts = new ButtonSetting(armorAlertsGroup, "Enabled", true));
        this.registerSetting(armorAlertsPingSound = new ButtonSetting(armorAlertsGroup, "Ping sound", true));
        this.registerSetting(armorAlertsChain = new ButtonSetting(armorAlertsGroup, "Chainmail armor", true));
        this.registerSetting(armorAlertsIron = new ButtonSetting(armorAlertsGroup, "Iron armor", true));
        this.registerSetting(armorAlertsDiamond = new ButtonSetting(armorAlertsGroup, "Diamond armor", true));

        this.closetModule = true;
    }

    public void onDisable() {
        entitySpawnQueue.clear();
        spawnedMobs.clear();
        alertTickCounter = 0;
        playerUpgradeMap.clear();
        teamUpgradeMap.clear();
        currentPlayerArmorMap.clear();
        wasInBedwarsGame = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (Utils.nullCheck() && obsidian.isToggled()) {
            if (this.obsidianPos.isEmpty()) {
                return;
            }
            try {
                Iterator<Map.Entry<BlockPos, Long>> iterator = this.obsidianPos.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<BlockPos, Long> entry = iterator.next();
                    BlockPos blockPos = entry.getKey();
                    Long receivedMs = entry.getValue();

                    if (!(mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockObsidian) && Utils.timeBetween(System.currentTimeMillis(), receivedMs) >= 500) {
                        iterator.remove();
                        continue;
                    }
                    RenderUtils.renderBlock(blockPos, obsidianColor, false, true);
                }

            }
            catch (Exception exception) {}
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            // Note: this also fires on respawn (death) within the same match, not just on
            // joining a fresh game. playerUpgradeMap/teamUpgradeMap/currentPlayerArmorMap are
            // intentionally NOT cleared here -- doing so used to wipe the "already alerted"
            // memory every time you died, causing every upgrade to be re-announced after a
            // respawn. Those maps only get reset once per actual new match, in onUpdate().
            lastHeldMap.clear();
            obsidianPos.clear();
            entitySpawnQueue.clear();
            spawnedMobs.clear();
        }
        else {
            if (e.entity != null && e.entity instanceof EntityIronGolem) {
                if (!isBedwarsGameActive()) {
                    return;
                }
                Vec3 spawnPosition = new Vec3(e.entity.posX, e.entity.posY, e.entity.posZ);
                for (SkyWars.SpawnEggInfo eggInfo : entitySpawnQueue) {
                    if (eggInfo.spawnPos.distanceTo(spawnPosition) > 3 || Utils.timeBetween(mc.thePlayer.ticksExisted, eggInfo.tickSpawned) > 60) { // 3 seconds or not at spawn point then not own mob
                        return;
                    }
                    if (!entitySpawnQueue.remove(eggInfo)) {
                        return;
                    }
                    spawnedMobs.add(e.entity.getEntityId());
                }
            }
        }
    }

    // Some servers (PikaNetwork included) broadcast every team upgrade purchase directly in
    // chat as "Team Upgrade - <player> purchased <upgrade>." This is far more reliable than
    // scanning inventories, since it works for every upgrade type, not just enchanted gear.
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!upgradesAlert.isToggled() || !Utils.nullCheck() || !isBedwarsGameActive()) {
            return;
        }

        String msg = Utils.stripColor(event.message.getUnformattedText());
        String lowerMsg = msg.toLowerCase(Locale.ROOT);
        int markerIndex = lowerMsg.indexOf(UPGRADE_CHAT_MARKER.toLowerCase(Locale.ROOT));
        if (markerIndex == -1) {
            return;
        }

        String rest = msg.substring(markerIndex + UPGRADE_CHAT_MARKER.length()).trim();
        while (rest.startsWith(":") || rest.startsWith("-") || rest.startsWith("–") || rest.startsWith("—")) {
            rest = rest.substring(1).trim();
        }
        String lowerRest = rest.toLowerCase(Locale.ROOT);
        int separatorIndex = lowerRest.indexOf(UPGRADE_CHAT_SEPARATOR);
        if (separatorIndex == -1) {
            return;
        }

        String playerName = rest.substring(0, separatorIndex).trim();
        String upgrade = rest.substring(separatorIndex + UPGRADE_CHAT_SEPARATOR.length()).trim();
        if (upgrade.endsWith(".")) {
            upgrade = upgrade.substring(0, upgrade.length() - 1).trim();
        }
        if (playerName.isEmpty() || upgrade.isEmpty()) {
            return;
        }

        this.notifyUpgradeForPlayer(playerName, upgrade);
    }

    @Override
    public void onUpdate() {
        boolean inBedwarsGame = isBedwarsGameActive();
        if (inBedwarsGame && !this.wasInBedwarsGame) {
            // Rising edge: a brand new match just started (as opposed to just respawning
            // within the same match). Reset "already alerted" memory here, and only here,
            // so upgrades don't get wiped and re-announced every time you die.
            this.playerUpgradeMap.clear();
            this.teamUpgradeMap.clear();
            this.currentPlayerArmorMap.clear();
        }
        this.wasInBedwarsGame = inBedwarsGame;

        if (inBedwarsGame) {
            if (enderPearl.isToggled() || obsidian.isToggled()) {
                for (EntityPlayer p : mc.theWorld.playerEntities) {
                    if (p == null) {
                        continue;
                    }
                    if (p == mc.thePlayer) {
                        continue;
                    }
                    if (AntiBot.isBot(p)) {
                        continue;
                    }
                    String name = p.getName();
                    ItemStack item = p.getHeldItem();
                    if (item != null && !lastHeldMap.containsKey(name)) {
                        String itemType = getItemType(item);
                        if (itemType != null) {
                            lastHeldMap.put(name, itemType);
                            double distance = Math.round(mc.thePlayer.getDistanceToEntity(p));
                            handleAlert(itemType, p.getDisplayName().getFormattedText(), Utils.asWholeNum(distance));
                        }
                    } else if (lastHeldMap.containsKey(name)) {
                        String itemType = lastHeldMap.get(name);
                        if (!itemType.equals(getItemType(item))) {
                            lastHeldMap.remove(name);
                        }
                    }
                }
            }

            if ((upgradesAlert.isToggled() || armorAlerts.isToggled()) && Utils.nullCheck() && !mc.thePlayer.isSpectator() && ++this.alertTickCounter % 20 == 0) {
                for (EntityPlayer p : mc.theWorld.playerEntities) {
                    if (p == null || p == mc.thePlayer || p.isDead || p.getName().isEmpty()) {
                        continue;
                    }
                    // Note: AntiBot.isBot() is intentionally NOT used here. Its heuristic flags
                    // any player who hasn't taken damage yet (full health, maxHurtTime == 0) as
                    // a possible bot, which wrongly matches real teammates/enemies early-to-mid
                    // game -- exactly when upgrades get bought -- causing missed alerts.

                    if (upgradesAlert.isToggled()) {
                        this.checkForUpgrades(p);
                    }
                    if (armorAlerts.isToggled()) {
                        this.processArmorAlert(p);
                    }
                }
            }
        }
    }

    private void checkForUpgrades(EntityPlayer player) {
        if (player == null) {
            return;
        }

        String playerName = player.getName();
        Set<String> upgrades = this.playerUpgradeMap.getOrDefault(playerName, new HashSet<>());

        // Match Nametags' durability rendering: pull gear via getEquipmentInSlot(), which is
        // kept in sync by entity-equipment packets for every player (including remote ones).
        // inventory.armorInventory / getHeldItem() go through the inventory container instead,
        // which isn't reliably populated for anyone but the local player -- that mismatch is
        // why enchant upgrades on teammates/enemies were getting missed.
        ItemStack heldItem = player.getEquipmentInSlot(0);
        if (heldItem != null && heldItem.getItem() instanceof ItemSword && heldItem.isItemEnchanted()) {
            int sharpnessLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, heldItem);
            if (sharpnessLevel > 0) {
                this.notifyUpgrade(playerName, player, "Sharpness " + sharpnessLevel, upgrades);
            }
        }

        Map<Enchantment, Integer> bestLevels = new LinkedHashMap<>();
        for (int equipSlot = 1; equipSlot <= 4; equipSlot++) { // 1=boots, 2=leggings, 3=chestplate, 4=helmet
            ItemStack armorPiece = player.getEquipmentInSlot(equipSlot);
            if (armorPiece == null || !(armorPiece.getItem() instanceof ItemArmor) || !armorPiece.isItemEnchanted()) {
                continue;
            }

            for (Enchantment enchant : ARMOR_ENCHANT_NAMES.keySet()) {
                int level = EnchantmentHelper.getEnchantmentLevel(enchant.effectId, armorPiece);
                if (level <= 0) {
                    continue;
                }
                Integer current = bestLevels.get(enchant);
                if (current == null || level > current) {
                    bestLevels.put(enchant, level);
                }
            }
        }

        for (Map.Entry<Enchantment, Integer> entry : bestLevels.entrySet()) {
            String enchantName = ARMOR_ENCHANT_NAMES.get(entry.getKey());
            String label = enchantName + " " + entry.getValue();
            this.notifyUpgrade(playerName, player, label, upgrades);
        }

        this.playerUpgradeMap.put(playerName, upgrades);
    }

    private void processArmorAlert(EntityPlayer player) {
        if (player == null) {
            return;
        }

        ItemStack leggings = player.getEquipmentInSlot(2); // 0=held, 1=boots, 2=leggings, 3=chestplate, 4=helmet
        if (leggings == null || !(leggings.getItem() instanceof ItemArmor)) {
            return;
        }

        String playerName = player.getName();
        String armorType = ((ItemArmor) leggings.getItem()).getArmorMaterial().name();
        String currentArmorType = this.currentPlayerArmorMap.get(playerName);
        if (armorType.equals(currentArmorType)) {
            return;
        }

        String displayName = player.getDisplayName().getFormattedText();

        if (this.armorAlertsDiamond.isToggled() && armorType.equals("DIAMOND")) {
            this.armorAlert(displayName, "&bDiamond Armor");
        }
        else if (this.armorAlertsIron.isToggled() && armorType.equals("IRON")) {
            this.armorAlert(displayName, "&fIron Armor");
        }
        else if (this.armorAlertsChain.isToggled() && armorType.equals("CHAIN")) {
            this.armorAlert(displayName, "&8Chainmail Armor");
        }

        this.currentPlayerArmorMap.put(playerName, armorType);
    }

    private void notifyUpgradeForPlayer(String playerName, String upgrade) {
        Set<String> upgrades = this.playerUpgradeMap.getOrDefault(playerName, new HashSet<>());
        EntityPlayer player = mc.theWorld != null ? mc.theWorld.getPlayerEntityByName(playerName) : null;
        this.notifyUpgrade(playerName, player, upgrade, upgrades);
        this.playerUpgradeMap.put(playerName, upgrades);
    }

    private void notifyUpgrade(String playerName, EntityPlayer player, String upgrade, Set<String> upgrades) {
        if (!Utils.nullCheck()) {
            return;
        }

        EnumChatFormatting color = this.resolveTeamColor(playerName, player);
        String teamName = this.getFormattedTeamName(color);
        if (teamName == null) {
            teamName = player != null ? player.getDisplayName().getFormattedText() : "&b" + playerName;
        }
        String teamKey = Utils.stripColor(teamName);

        Set<String> teamUpgrades = this.teamUpgradeMap.getOrDefault(teamKey, new HashSet<>());
        if (teamUpgrades.contains(upgrade)) {
            return;
        }

        String upgradeMessage = teamName + " &7purchased &3" + upgrade;
        Utils.sendMessage(upgradeMessage);

        upgrades.add(upgrade);
        teamUpgrades.add(upgrade);
        this.teamUpgradeMap.put(teamKey, teamUpgrades);

        if (this.upgradesPingSound.isToggled()) {
            mc.thePlayer.playSound("note.pling", 1.0f, 1.0f);
        }
    }

    // Primary team detection: read the dyed leather color off the player's helmet + chestplate.
    // Bed Wars keeps those two slots leather-dyed to the team color for the whole game, even
    // once boots/leggings get upgraded to iron/chain/diamond, so this works regardless of
    // scoreboard state. Falls back to the scoreboard team lookup if no player entity is known
    // or their armor isn't (yet) dyed leather -- e.g. right at spawn before kit gear applies.
    private EnumChatFormatting resolveTeamColor(String playerName, EntityPlayer player) {
        if (player != null) {
            EnumChatFormatting armorColor = this.getTeamColorFromArmor(player);
            if (armorColor != null) {
                return armorColor;
            }
        }

        if (mc.theWorld != null && mc.theWorld.getScoreboard() != null) {
            ScorePlayerTeam playerTeam = mc.theWorld.getScoreboard().getPlayersTeam(playerName);
            if (playerTeam != null) {
                return playerTeam.getChatFormat();
            }
        }

        return null;
    }

    private EnumChatFormatting getTeamColorFromArmor(EntityPlayer player) {
        for (int slot : new int[]{3, 4}) { // 3=chestplate, 4=helmet
            ItemStack piece = player.getEquipmentInSlot(slot);
            if (piece == null || !(piece.getItem() instanceof ItemArmor)) {
                continue;
            }

            ItemArmor armorItem = (ItemArmor) piece.getItem();
            if (armorItem.getArmorMaterial() != ItemArmor.ArmorMaterial.LEATHER) {
                continue;
            }

            int rgb = armorItem.getColor(piece);
            EnumChatFormatting match = closestTeamColor(rgb);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static EnumChatFormatting closestTeamColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        EnumChatFormatting best = null;
        long bestDist = Long.MAX_VALUE;
        for (Map.Entry<EnumChatFormatting, Integer> entry : TEAM_ARMOR_COLORS.entrySet()) {
            int c = entry.getValue();
            int cr = (c >> 16) & 0xFF;
            int cg = (c >> 8) & 0xFF;
            int cb = c & 0xFF;
            long dist = (long) (r - cr) * (r - cr) + (long) (g - cg) * (g - cg) + (long) (b - cb) * (b - cb);
            if (dist < bestDist) {
                bestDist = dist;
                best = entry.getKey();
            }
        }
        return best;
    }

    private String getFormattedTeamName(EnumChatFormatting color) {
        if (color == null) {
            return null;
        }
        switch (color) {
            case RED:
                return "&cRed";
            case BLUE:
                return "&9Blue";
            case GREEN:
                return "&aGreen";
            case YELLOW:
                return "&eYellow";
            case AQUA:
                return "&bAqua";
            case WHITE:
                return "&fWhite";
            case LIGHT_PURPLE:
                return "&dPink";
            case DARK_GRAY:
                return "&8Gray";
            default:
                return null;
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) e.getPacket();
            if (p.getPlacedBlockDirection() != 255 && p.getStack() != null && p.getStack().getItem() != null) {
                if (p.getStack().getItem() instanceof ItemMonsterPlacer) {
                    Class<? extends Entity> oclass = EntityList.stringToClassMapping.get(ItemMonsterPlacer.getEntityName(p.getStack()));
                    if (oclass == null) {
                        return;
                    }
                    if (oclass.getSimpleName().equals("EntityIronGolem")) {
                        entitySpawnQueue.add(new SkyWars.SpawnEggInfo(p.getPosition(), mc.thePlayer.ticksExisted));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange p = (S23PacketBlockChange) e.getPacket();
            if (p.getBlockState() != null && p.getBlockState().getBlock() instanceof BlockObsidian && isNextToBed(p.getBlockPosition())) {
                this.obsidianPos.put(p.getBlockPosition(), System.currentTimeMillis());
            }
        }
    }

    private boolean isNextToBed(BlockPos blockPos) {
        for (EnumFacing enumFacing : EnumFacing.values()) {
            BlockPos offset = blockPos.offset(enumFacing);
            if (BlockUtils.getBlock(offset) instanceof BlockBed) {
                return true;
            }
        }
        return false;
    }

    private boolean isBedwarsGameActive() {
        int status = Utils.getBedwarsStatus();
        if (status == 2) {
            return true;
        }
        if (!Utils.nullCheck()) {
            return false;
        }
        for (String line : Utils.getSidebarLines()) {
            String clean = Utils.stripString(line).toUpperCase(Locale.ROOT);
            if (clean.contains("BED WARS") || clean.contains("BEDWARS")) {
                return true;
            }
            if (clean.startsWith("R RED:") || clean.startsWith("B BLUE:") || clean.startsWith("G GREEN:") || clean.startsWith("Y YELLOW:")) {
                return true;
            }
        }
        return false;
    }

    private String getItemType(ItemStack item) {
        if (item == null || item.getItem() == null) {
            return null;
        }
        String unlocalizedName = item.getItem().getUnlocalizedName();
        if (item.getItem() instanceof ItemEnderPearl && enderPearl.isToggled()) {
            return "&7an §3Ender Pearl";
        }
        else if (unlocalizedName.contains("tile.obsidian") && obsidian.isToggled()) {
            return "§dObsidian";
        }
        else if (item.getItem() instanceof ItemFireball && fireball.isToggled()) {
            return "&7a §6Fireball";
        }
        return null;
    }

    private void handleAlert(String itemType, String name, String info) {
        String message = name + " &7is holding " + itemType + " &7(" + "§d" + info + "m" + "&7)";
        Utils.sendMessage("&eAlert: &r" + message);
        ping();
    }

    private void armorAlert(String displayName, String armorLabel) {
        String armorMessage = displayName + " &7purchased " + armorLabel;
        Utils.sendMessage(armorMessage);
        if (this.armorAlertsPingSound.isToggled()) {
            mc.thePlayer.playSound("note.pling", 1.0f, 1.0f);
        }
    }

    private void ping() {
        if (shouldPing.isToggled()) {
            mc.thePlayer.playSound("note.pling", 1.0f, 1.0f);
        }
    }
}
