package ravens4.module.impl.movement;

import ravens4.module.Module;
import ravens4.module.setting.impl.DescriptionSetting;

public class MovementFix extends Module {

    public MovementFix() {
        super("Movement Fix", category.movement);
        this.registerSetting(new DescriptionSetting("Aligns input with rotations"));
    }
}
