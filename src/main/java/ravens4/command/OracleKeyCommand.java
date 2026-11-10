package ravens4.command;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import ravens4.module.ModuleManager;
import ravens4.module.impl.other.Oracle;
import ravens4.utility.Utils;

/**
 * Client side "/oracle" command. Because it is a registered client command it is never sent to the server,
 * so the API key typed here stays on your device. Answers go straight to the sender, so you always get feedback.
 */
public class OracleKeyCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "oracle";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            run(sender, args);
        } catch (Throwable t) {
            say(sender, "&cSomething went wrong (" + t.getClass().getSimpleName() + "). Try the command again.");
        }
    }

    private void run(ICommandSender sender, String[] args) {
        Oracle oracle = ModuleManager.oracle;
        if (oracle == null) {
            say(sender, "&cThe Oracle module isn't loaded.");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();

        if (sub.equals("key")) {
            if (args.length < 2) {
                say(sender, "&7Usage: &f/oracle key <your Groq key>&7  (&f/oracle key clear&7 removes it)");
                return;
            }
            if (args[1].equalsIgnoreCase("clear")) {
                boolean ok = oracle.setApiKey("");
                say(sender, ok ? "&aKey removed." : "&cCouldn't update " + oracle.getConfigPath());
                return;
            }
            String key = args[1].trim();
            // people often paste the <key> placeholder brackets or quotes along with it
            key = key.replaceAll("^[<\"'`]+|[>\"'`]+$", "");
            if (key.length() < 10) {
                say(sender, "&cThat doesn't look like a key (too short). Copy the whole key from console.groq.com.");
                return;
            }
            if (!key.startsWith("gsk_")) {
                say(sender, "&eGroq keys normally start with &fgsk_&e. Saving it anyway, then checking it.");
            }
            if (!oracle.setApiKey(key)) {
                say(sender, "&cCouldn't save the key: couldn't write &f" + oracle.getConfigPath());
                return;
            }
            say(sender, "&aKey saved &7(" + oracle.maskedKey() + ") &7in config/oracle.properties on this device.");
            say(sender, "&7Checking it with Groq...");
            oracle.verifyKey(msg -> Minecraft.getMinecraft().addScheduledTask(() -> say(sender, msg)));
        } else if (sub.equals("model")) {
            if (args.length < 2) {
                say(sender, "&7Current model: &f" + oracle.getModel() + "&7. Change it with &f/oracle model <name>&7, list them with &f/oracle models");
                return;
            }
            boolean ok = oracle.setModel(args[1]);
            say(sender, ok ? "&aModel set to &f" + oracle.getModel() : "&cCouldn't save the model to " + oracle.getConfigPath());
        } else if (sub.equals("models")) {
            oracle.listModels();
        } else if (sub.equals("status")) {
            say(sender, "&7Key: " + (oracle.hasKey() ? "&asaved &7(" + oracle.maskedKey() + ")" : "&cmissing")
                    + "&7, model &f" + oracle.getModel()
                    + "&7, notes &f" + oracle.noteCount()
                    + "&7, module " + (oracle.isEnabled() ? "&aon" : "&coff"));
        } else {
            say(sender, "&f/oracle key <key>&7, &f/oracle model <name>&7, &f/oracle models&7, &f/oracle status");
        }
    }

    private static void say(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(Utils.formatColor("&dOracle&7: " + message)));
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/oracle key <key>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
