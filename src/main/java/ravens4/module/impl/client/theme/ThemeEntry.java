package ravens4.module.impl.client.theme;

import ravens4.module.Module;
import ravens4.utility.ClientTheme;

/**
 * One entry of the Theme category. It is "on" while its theme is the selected one; clicking it selects the theme.
 * It never joins the enabled-module list, so it does not show up in the HUD.
 */
public class ThemeEntry extends Module {
    private final int index;

    public ThemeEntry(String name, int index) {
        super(name, category.theme, 0);
        this.index = index;
    }

    @Override
    public boolean isEnabled() {
        return ClientTheme.isSelected(this.index);
    }

    @Override
    public void toggle() {
        ClientTheme.select(this.index);
    }

    @Override
    public void enable() {
    }

    @Override
    public void disable() {
    }
}
