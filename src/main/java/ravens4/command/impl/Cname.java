package ravens4.command.impl;

import ravens4.command.Command;
import ravens4.command.CommandInput;
import ravens4.module.impl.other.NameHider;

public class Cname extends Command {
    public Cname() {
        super("namehider");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            syntaxError();
            return;
        }

        NameHider.setFakeName(input.joinArguments(0));
        replyWithHeader("&7Name has been set to &b" + NameHider.fakeName + "&7.");
    }
}
