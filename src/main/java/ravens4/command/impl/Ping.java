package ravens4.command.impl;

import ravens4.command.Command;
import ravens4.command.CommandInput;
import ravens4.helper.PingHelper;

public class Ping extends Command {
    public Ping() {
        super("ping");
    }

    @Override
    public void execute(CommandInput input) {
        PingHelper.checkPing(true);
    }
}
