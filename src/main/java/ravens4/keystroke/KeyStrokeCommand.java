package ravens4.keystroke;

import ravens4.Raven;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class KeyStrokeCommand extends CommandBase {
    public String getCommandName() {
        return "solace";
    }

    /** The old name keeps working. */
    @Override
    public java.util.List<String> getCommandAliases() {
        return java.util.Collections.singletonList("ravens4");
    }

    public void processCommand(ICommandSender sender, String[] args) {
        Raven.toggleKeyStrokeConfigGui();
    }

    public String getCommandUsage(ICommandSender sender) {
        return "/solace";
    }

    public int getRequiredPermissionLevel() {
        return 0;
    }

    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
