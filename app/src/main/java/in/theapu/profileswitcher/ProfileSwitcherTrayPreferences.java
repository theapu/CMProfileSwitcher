package in.theapu.profileswitcher;

/**
 * Created by apu on 15/7/16.
 */
import net.grandcentrix.tray.TrayPreferences;
import android.content.Context;


public class ProfileSwitcherTrayPreferences extends TrayPreferences {

    public ProfileSwitcherTrayPreferences (final Context context) {
        super(context, "ProfileSwitcherPreferences", 1);
    }

}
