package ravens4.module.impl.render;

import ravens4.module.Module;
import ravens4.module.setting.impl.DescriptionSetting;
import ravens4.module.setting.impl.SliderSetting;

public class NoHurtCam extends Module {
    public SliderSetting multiplier;
    public NoHurtCam() {
        super("NoHurtCam", category.render);
        this.registerSetting(new DescriptionSetting("Default is 14x multiplier."));
        this.registerSetting(multiplier = new SliderSetting("Multiplier", 14, -40, 40, 1));
    }
}
