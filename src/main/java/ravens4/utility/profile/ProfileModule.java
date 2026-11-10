package ravens4.utility.profile;

import ravens4.Raven;
import ravens4.clickgui.ClickGui;
import ravens4.module.Module;
import ravens4.module.impl.client.Settings;
import ravens4.module.setting.impl.ButtonSetting;
import ravens4.module.setting.impl.TextSetting;
import ravens4.utility.Utils;

public class ProfileModule extends Module {
    private final Profile profile;
    private final TextSetting profileNameSetting;
    private String displayName;
    public boolean saved = true;

    public ProfileModule(Profile profile, String name, int bind) {
        super(name, category.profiles, bind);
        this.profile = profile;
        this.displayName = name;
        this.registerSetting(profileNameSetting = new TextSetting("Profile name", name, "Type a new profile name...", 32, this::renameProfile));
        this.registerSetting(new ButtonSetting("Save profile", () -> {
            Utils.sendMessage("&7Saved profile: &b" + getName());
            Raven.profileManager.saveProfile(this.profile);
            saved = true;
        }));
        this.registerSetting(new ButtonSetting("Remove profile", () -> {
            String profileName = getName();
            if (Raven.profileManager.deleteProfile(profileName)) {
                Utils.sendMessage("&7Removed profile: &b" + profileName);
            }
        }));
    }

    @Override
    public void toggle() {
        if (mc.currentScreen instanceof ClickGui || mc.currentScreen == null) {
            Raven.profileManager.loadProfile(this.getName());

            Raven.currentProfile = profile;

            if (Settings.sendMessage.isToggled()) {
                Utils.sendMessage("&7Enabled profile: &b" + this.getName());
            }
            saved = true;
        }
    }

    @Override
    public boolean isEnabled() {
        if (Raven.currentProfile == null) {
            return false;
        }
        return Raven.currentProfile.getModule() == this;
    }

    @Override
    public String getName() {
        return displayName;
    }

    public void setProfileName(String profileName) {
        this.displayName = profileName;
        profileNameSetting.setText(profileName);
    }

    private void renameProfile() {
        if (Raven.profileManager == null) {
            return;
        }

        String oldName = getName();
        if (Raven.profileManager.renameProfile(profile, profileNameSetting.getText())) {
            profileNameSetting.setText(profile.getName());
            if (!oldName.equals(profile.getName())) {
                Utils.sendMessage("&7Renamed profile: &b" + oldName + " &7to &b" + profile.getName());
            }
        }
    }
}
