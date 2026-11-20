package ravens4.module.impl.client.theme;

import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.DescriptionSetting;

public class ThemeSettings extends Module {
    public static ButtonSetting applyToRender;

    public ThemeSettings() {
        super("Theme Settings", category.theme, 0);
        this.registerSetting(new DescriptionSetting("Click a theme below to use it everywhere"));
        this.registerSetting(applyToRender = new ButtonSetting("Apply to render modules", true));
        this.canBeEnabled = false;
    }

    public static boolean isApplyToRenderEnabled() {
        return applyToRender == null || applyToRender.isToggled();
    }
}
