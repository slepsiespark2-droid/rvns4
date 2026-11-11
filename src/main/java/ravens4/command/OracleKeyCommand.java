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
        } else if (sub.equals("help") || sub.equals("?")) {
            help(sender);
        } else if (sub.equals("ask")) {
            if (!oracle.isEnabled()) {
                say(sender, "&7Turn the &dOracle&7 module on first.");
            } else if (args.length < 2) {
                say(sender, "&7Usage: &f/oracle ask <question>");
            } else {
                oracle.ask(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
        } else if (sub.equals("system-prompt") || sub.equals("prompt")) {
            systemPrompt(sender, oracle, args);
        } else if (sub.equals("auto-message") || sub.equals("auto")) {
            if (args.length < 2) {
                say(sender, "&7Auto message is " + (oracle.isAutoMessage() ? "&aon" : "&coff") + "&7. Use &f/oracle auto-message on&7 or &foff");
            } else if (args[1].equalsIgnoreCase("on")) {
                oracle.setAutoMessage(true);
                say(sender, "&aAuto message on. &7Oracle answers when someone says &foracle&7 in chat: you get full access, other players get a polite chat only and are refused if rude.");
                say(sender, "&eMany servers ban automated chat. Use it only where it's allowed.");
            } else if (args[1].equalsIgnoreCase("off")) {
                oracle.setAutoMessage(false);
                say(sender, "&cAuto message off.");
            } else {
                say(sender, "&7Use &f/oracle auto-message on&7 or &foff");
            }
        } else if (sub.equals("fallback") || sub.equals("fallbacks")) {
            fallback(sender, oracle, args);
        } else if (sub.equals("session")) {
            say(sender, "&7Session memory: " + oracle.sessionStatus());
        } else if (sub.equals("block")) {
            if (args.length < 2) {
                say(sender, "&7Usage: &f/oracle block <player>&7 (Oracle will never answer them)");
            } else {
                oracle.blockPlayer(args[1]);
                say(sender, "&aOracle will ignore &f" + args[1] + "&a.");
            }
        } else if (sub.equals("unblock")) {
            if (args.length < 2) {
                say(sender, "&7Usage: &f/oracle unblock <player>");
            } else {
                say(sender, oracle.unblockPlayer(args[1]) ? "&aOracle will answer &f" + args[1] + "&a again." : "&7" + args[1] + " wasn't blocked.");
            }
        } else if (sub.equals("blocked")) {
            java.util.List<String> list = oracle.getBlocked();
            say(sender, list.isEmpty() ? "&7Nobody is blocked." : "&7Blocked: &f" + String.join(", ", list));
        } else if (sub.equals("models")) {
            oracle.listModels();
        } else if (sub.equals("status")) {
            say(sender, "&7Key: " + (oracle.hasKey() ? "&asaved &7(" + oracle.maskedKey() + ")" : "&cmissing")
                    + "&7, model &f" + oracle.getModel()
                    + "&7, backups &f" + oracle.getFallbacks().size()
                    + "&7, notes &f" + oracle.noteCount()
                    + "&7, module " + (oracle.isEnabled() ? "&aon" : "&coff")
                    + "&7, auto message " + (oracle.isAutoMessage() ? "&aon" : "&coff"));
        } else {
            say(sender, "&7Type &f/oracle help&7 to see every command.");
        }
    }

    private void fallback(ICommandSender sender, Oracle oracle, String[] args) {
        String action = args.length < 2 ? "list" : args[1].toLowerCase();
        if (action.equals("add") && args.length >= 3) {
            say(sender, oracle.addFallback(args[2]) ? "&aBackup model added: &f" + args[2] : "&cCouldn't save the backup list.");
        } else if ((action.equals("remove") || action.equals("rm")) && args.length >= 3) {
            say(sender, oracle.removeFallback(args[2]) ? "&aRemoved &f" + args[2] : "&7" + args[2] + " wasn't in the list.");
        } else if (action.equals("clear")) {
            oracle.clearFallbacks();
            say(sender, "&aBackup models cleared.");
        } else {
            java.util.List<String> list = oracle.getFallbacks();
            say(sender, "&7Main model: &f" + oracle.getModel());
            say(sender, list.isEmpty() ? "&7Backup models: none. Add one with &f/oracle fallback add <model>"
                    : "&7Backup models, tried in order: &f" + String.join(", ", list));
            say(sender, "&7Change with &f/oracle fallback add|remove <model>&7 or &fclear");
        }
    }

    private void help(ICommandSender sender) {
        say(sender, "&7Commands (&f/oracle ...&7):");
        say(sender, "&f key <key>&7: save and check your Groq key (&fkey clear&7 removes it)");
        say(sender, "&f model <name>&7 / &fmodels&7: pick a model / list models");
        say(sender, "&f system-prompt <text>&7: set the prompt (&fadd <text>&7, &fshow&7, &freset&7)");
        say(sender, "&f auto-message on|off&7: answer \"oracle\" in server chat (you = full access, others = chat only)");
        say(sender, "&f block <player>&7 / &funblock <player>&7 / &fblocked&7: players Oracle ignores");
        say(sender, "&f ask <question>&7: ask without a dot command");
        say(sender, "&f fallback add|remove <model>&7: backup models used when the main one is limited or broken");
        say(sender, "&f session&7: what Oracle remembers from this session (&f.ask clear&7 wipes it)");
        say(sender, "&f status&7: key, model, notes, auto message");
        say(sender, "&7With the module on: &f.ask <question>&7, &f.ask memory&7, &f.ask forget <n|all>&7, &f.ask clear");
        say(sender, "&7Configs: &f\"save my settings as pvp\"&7, &f\"load pvp\"&7, &f\"delete the old config\"&7 (old copies are kept in profile-backups)");
        say(sender, "&7Ask in plain words: &f\"turn on velocity\"&7, &f\"set reach to 3\"&7, &f\"tell everyone gg\"&7, &f\"what did they say?\"");
    }

    private void systemPrompt(ICommandSender sender, Oracle oracle, String[] args) {
        if (args.length < 2) {
            say(sender, "&7Usage: &f/oracle system-prompt <text>&7, &fadd <text>&7, &fshow&7, &freset");
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("reset")) {
            oracle.resetSystemPrompt();
            say(sender, "&aSystem prompt reset to the default.");
            return;
        }
        if (action.equals("show")) {
            String p = oracle.getSystemPrompt();
            say(sender, "&7Current prompt (&f" + p.length() + "&7 characters):");
            for (int i = 0; i < p.length() && i < 600; i += 60) {
                sender.addChatMessage(new ChatComponentText(Utils.formatColor("&f" + p.substring(i, Math.min(p.length(), i + 60)))));
            }
            return;
        }
        boolean append = action.equals("add");
        int from = append ? 2 : 1;
        if (args.length <= from) {
            say(sender, "&7Add some text after &f" + args[1] + "&7.");
            return;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
        if (oracle.setSystemPrompt(text, append)) {
            say(sender, "&a" + (append ? "Added to the system prompt." : "System prompt saved.") + " &7(&f" + oracle.getSystemPrompt().length() + "&7 characters)");
        } else {
            say(sender, "&cCouldn't save the prompt to " + oracle.getConfigPath());
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
