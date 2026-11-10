package ravens4.module.impl.client;

import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.utility.CommandHandler;
import ravens4.utility.Timer;

public class CommandLine extends Module {
    public static boolean opened = false;
    public static boolean closed = false;

    public static Timer animation;
    public static ButtonSetting animate;

    public CommandLine() {
        super("Command line", category.client);
        this.registerSetting(animate = new ButtonSetting("Animate", true));
    }

    @Override
    public void onEnable() {
        CommandHandler.setBackgroundColor();
        opened = true;
        closed = false;
        (animation = new Timer(500.0F)).start();
    }

    @Override
    public void onDisable() {
        closed = true;
        if (animation != null) {
            animation.start();
        }

        CommandHandler.onDisable();
    }
}
