package ravens4.command.impl;

import ravens4.command.Command;
import ravens4.command.CommandInput;
import ravens4.utility.Utils;
import ravens4.utility.system.SystemUtils;

public class Name extends Command {
    public Name() {
        super("name", "ign", "name");
    }

    @Override
    public void execute(CommandInput input) {
        if (!Utils.nullCheck()) {
            return;
        }

        SystemUtils.addToClipboard(mc.thePlayer.getName());
        replyWithHeader("&7Copied &b" + mc.thePlayer.getName() + " &7to clipboard");
    }
}
