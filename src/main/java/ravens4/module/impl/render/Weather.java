package ravens4.module.impl.render;

import ravens4.module.Module;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.SliderSetting;

public class Weather extends Module {
    public SliderSetting time;
    public SliderSetting lightning;
    public ButtonSetting rain;

    public Weather() {
        super("Weather", category.render);
        this.registerSetting(time = new SliderSetting("Time", 0, 0, 24, 0.1));
        this.registerSetting(lightning = new SliderSetting("Lightning", 0, 0, 1, 0.01));
        this.registerSetting(rain = new ButtonSetting("Rain", false));
    }
}
