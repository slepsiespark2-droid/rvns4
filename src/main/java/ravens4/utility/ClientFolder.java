package ravens4.utility;

import net.minecraft.client.Minecraft;

import java.io.File;

/**
 * The client's data folder inside .minecraft (profiles, scripts, settings, relations).
 * It used to be called "RavenS4"; an old folder is renamed to "Solace" the first time it is needed so nothing is lost.
 */
public final class ClientFolder {
    private static final String NAME = "Solace";
    private static final String LEGACY_NAME = "RavenS4";

    private static File folder;

    private ClientFolder() {
    }

    public static synchronized File get() {
        if (folder == null) {
            File gameDirectory = Minecraft.getMinecraft().mcDataDir;
            File current = new File(gameDirectory, NAME);
            File legacy = new File(gameDirectory, LEGACY_NAME);
            if (!current.exists() && legacy.exists() && !legacy.renameTo(current)) {
                // couldn't move it (permissions, locked files...): keep using the old folder instead of losing the data
                folder = legacy;
            }
            else {
                folder = current;
            }
        }
        return folder;
    }
}
