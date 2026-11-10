package ravens4.command.impl;

import ravens4.command.Command;
import ravens4.command.CommandInput;
import ravens4.module.Module;
import ravens4.module.ModuleManager;

public class HideAll extends Command {
    public HideAll() {
        super("hide", "hideall");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() > 0 && !"all".equalsIgnoreCase(input.getArgument(0))) {
            syntaxError();
            return;
        }

        int count = 0;
        for (Module module : ModuleManager.modules) {
            if (!module.isHidden()) {
                module.setHidden(true);
                count++;
            }
        }
        replyWithHeader("&7Hidden &c" + count + " &7module" + (count == 1 ? "" : "s") + " from HUD.");
    }
}
