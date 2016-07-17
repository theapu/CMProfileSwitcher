package in.theapu.profileswitcher;

/**
 * Created by apu on 15/7/16.
 */

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import android.app.AlarmManager;
import android.app.PendingIntent;
import cyanogenmod.app.Profile;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import android.os.RemoteException;
import cyanogenmod.app.ProfileManager;
import cyanogenmod.app.IProfileManager;

public class ProfileSwitcher {

    private static final String TAG = "ProfileSwitcher";

    private Context context;
    private String[] profileNames;
    private Boolean debugMode;
    private static Boolean resetOnHeadsetPlug;
    private static Boolean resetOnHeadsetUnplug;

    private static IProfileManager sService;

    private String calculatedCurrentScheduledProfile;
    private Calendar calculatedNextScheduledAlarm;
    private Calendar lastCalculationRun;


    static final int TIMED_ALARM = 1;  //Used for the pending intent requestCode
    static final int REGULAR_ALARM = 2;

    final ProfileSwitcherTrayPreferences preferences;

    public ProfileSwitcher(Context context) {
        this.context = context;
        preferences = new ProfileSwitcherTrayPreferences(this.context);
        debugMode = preferences.getBoolean("debug_mode", false);
        resetOnHeadsetPlug = preferences.getBoolean("reset_on_headset_plug", false);
        resetOnHeadsetUnplug = preferences.getBoolean("reset_on_headset_unplug", false);

        try{
            sService = ProfileManager.getService();
        }
        catch(NoClassDefFoundError e) {
            myToast("This app only works on a ROM with a ProfileManager (eg CM9 & CM10). You should exit now :-(");
            myLog("*** Unable to get ProfileManager service **** ");
        }
        getAllProfiles();

    }

    public boolean isDebugEnabled() {
        return debugMode;
    }

    public void toggleDebugMode() {
        debugMode = !debugMode;
        preferences.put("debug_mode", debugMode);
    }

    public static void myLog(String log) {
        Calendar now = Calendar.getInstance();
        Log.v(TAG, calendarToString(now) + " : " + log);
    }

    public void myToast(String toast) {
        if (isDebugEnabled()) {
            myLog("Sending toast:" + toast);
        }
        Toast.makeText(context, "ProfileSwitcher: " + toast, Toast.LENGTH_LONG).show();
    }

    public static String calendarToString(Calendar cal) {
        if (cal == null) {
            return "<never>";
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd E HH:mm");
        return formatter.format(cal.getTime());
    }

    public static String calendarToEHHmm(Calendar cal) {
        if (cal == null) {
            return "<never>";
        }
        SimpleDateFormat formatter = new SimpleDateFormat("E HH:mm");
        return formatter.format(cal.getTime());
    }

    public String[] getAllProfiles() {
         if (sService == null) {
            return new String[]{"no profile manger found"};
        } else {
            if (profileNames == null && sService != null) {
                try {
                    Profile[] allProfiles = sService.getProfiles();
                    profileNames = new String[allProfiles.length];
                    int i = 0;
                    for (Profile p : allProfiles) {
                        profileNames[i++] = p.getName();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                }
            }
            return profileNames;
        }
    }


    public String getActiveProfileName() {
         if (sService == null) {
            return "<error - no profile manager>";
        } else {
            try {
                return sService.getActiveProfile().getName();
            } catch (RemoteException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                return "<error getting active profile name>";
            }
        }
    }

    public boolean setActiveProfile(String profileName) {
        Calendar timedProfileExires = getTimedProfileExires();
        if (timedProfileExires != null) {
            if (timedProfileExires.before(Calendar.getInstance())) {
                //This is the first time setting a profile after a time profile
                //We will be setting the profile to the original profile before the timed profile
                //started (or a scheduled profile change that happened in the middle of timed profile)
                //Delete the timed profile settings now, so that we don't reset to this next time
                deleteTimedProfile();
            }
        }

            if (profileName == null) {
                myToast("Error setting profile to 'null'");
                return false;
            } else if (sService == null) {
                myToast("Error setting profile - no profile manager found");
                return false;
            }
            try {
                sService.setActiveProfileByName(profileName);
                myToast("Profile changed to " + profileName);
                return true;
            } catch (RemoteException e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                myToast("Error setting profile:" + e.getLocalizedMessage());
                return false;
            }
        }

    public void setNextAlarm() {
        Calendar nextAlarm = getNextScheduledAlarm();
        if (nextAlarm == null) {
            myToast("Error finding nextAlarm - not setting any");
        } else {
            myLog("Setting next alarm for " + calendarToEHHmm(nextAlarm));
            setAlarm(nextAlarm, REGULAR_ALARM);
        }
    }

    public boolean setAlarm(Calendar alarmTime, int alarmType) {
        Intent intent = new Intent(context, ScheduleBroadcastReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), alarmType, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pendingIntent);
        return true;
    }

    public void startService() {
        if (resetOnHeadsetPlug() || resetOnHeadsetUnplug()) {
            Intent service = new Intent(context, HeadsetService.class);
            context.startService(service);
        }
    }

    public void stopService() {
        Intent service = new Intent(context, HeadsetService.class);
        context.stopService(service);
    }

    public boolean resetOnHeadsetPlug() {
        return resetOnHeadsetPlug;
    }

    public void toggleResetOnHeadsetPlug() {
        resetOnHeadsetPlug = !resetOnHeadsetPlug;
        preferences.put("reset_on_headset_plug", resetOnHeadsetPlug);
        startService();
    }

    public boolean resetOnHeadsetUnplug() {
        return resetOnHeadsetUnplug;
    }

    public void toggleResetOnHeadsetUnplug() {
        resetOnHeadsetUnplug = !resetOnHeadsetUnplug;
        preferences.put("reset_on_headset_unplug", resetOnHeadsetUnplug);
        startService();
    }


    public void setTimedProfile(String timedProfileName, Calendar timedProfileExires) {

        preferences.put("timedProfileName", timedProfileName);
        preferences.put("timedProfileExires", timedProfileExires.getTimeInMillis());
        //If timedProfileToRevertToName already exists, then we are setting a timed profile
        //while already in a timed profile.  In this scenario, we would not want to
        //overwrite the timedProfileToRevertToName.
        if (getTimedProfileToRevertTo() == null) {
            preferences.put("timedProfileToRevertToName", getActiveProfileName());
            myLog("set in pref: timedProfileToRevertToName = " + getTimedProfileToRevertTo());
        }

        myLog("set in pref: timedProfileName = " + timedProfileName);
        myLog("set in pref: timedProfileExires = " + calendarToString(timedProfileExires));

        setAlarm(timedProfileExires, TIMED_ALARM);
    }

    public void deleteTimedProfile() {

        preferences.remove("timedProfileName");
        preferences.remove("timedProfileToRevertToName");
        preferences.remove("timedProfileExires");

        myLog("in deleteTimedProfile - removed timedProfileName, timedProfileToRevertToName, timedProfileExires");
    }


    public String getTimedProfileName() {
        //This is what the user set the profile to!
        String timedProfileName = preferences.getString("timedProfileName", null);
        myLog("got from pref: timedProfileName = " + timedProfileName);
        return timedProfileName;
    }


    public Calendar getTimedProfileExires() {
        //This is the expiry time for a timed profile
        long timedProfileExiresLong = preferences.getLong("timedProfileExires", 0);
        if (timedProfileExiresLong == 0) {
            return null;
        }
        Calendar timedProfileExires = Calendar.getInstance();
        timedProfileExires.setTimeInMillis(timedProfileExiresLong);
        myLog("got from pref: timedProfileExires = " + calendarToString(timedProfileExires));
        return timedProfileExires;
    }

    public String getTimedProfileToRevertTo() {
        //This was the profile before the user switched
        String timedProfileToRevertToName = preferences.getString("timedProfileToRevertToName", null);
        myLog("got from pref: timedProfileToRevertToName = " + timedProfileToRevertToName);
        return timedProfileToRevertToName;
    }

    public void setTimedProfileToRevertTo(String timedProfileToRevertTo) {

        preferences.put("timedProfileToRevertToName", timedProfileToRevertTo);
        myLog("set in pref: timedProfileToRevertToName = " + timedProfileToRevertTo);
    }


    public String getProfileToChangeTo() {
        Calendar timedProfileExires = getTimedProfileExires();
        if (timedProfileExires != null) {
            if (timedProfileExires.after(Calendar.getInstance())) {
                return getTimedProfileName();
            } else {
                return getTimedProfileToRevertTo();
            }
        }

        return getCurrentScheduledProfile();
    }

    public String getCurrentScheduledProfile() {
        //get the current profile as per the schedule
        calculateSchedule();
        return calculatedCurrentScheduledProfile;
    }

    public Calendar getNextScheduledAlarm() {
        //get the next alarm as per the schedule
        calculateSchedule();
        return calculatedNextScheduledAlarm;
    }

    public void signalPossibleChangesMade() {
        //(I'm not a fan of how this works .... but it works)
        //Force a recalculation.  Important if we think that a change has been made.
        lastCalculationRun = null;
    }

    private void calculateSchedule() {
        // This methods set the following variables...
        // 		calculatedCurrentScheduledProfile
        //		calculatedNextScheduledAlarm
        //		lastCalculationRun
        //  NB - This method does not consider 'timed profiles'
        //       In hindsight it should...

        ScheduleDataSource datasource = new ScheduleDataSource(context);
        datasource.open();
        List<Schedule> allSchedules = datasource.getAllSchedules();
        datasource.close();

        Calendar now = Calendar.getInstance();
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        if (now.equals(lastCalculationRun)) {
            if (isDebugEnabled()) {
                myLog("SPSP reusing existing calculation"
                        + calendarToString(now)
                        + calendarToString(lastCalculationRun));
            }
            //We already have calculated the schedule for this current minute. No need to do it again and again....
            return;
        }

        if (isDebugEnabled()) {
            myLog("SPSP performing calculation"
                    + calendarToString(now)
                    + calendarToString(lastCalculationRun));
        }
        Schedule nextSchedule = null;  //this is the next schedule that will be set at the next alarm
        Calendar nextScheduleAlarm = null;
        Schedule currentSchedule = null;  //this is the current schedule to be set (its start time may be now or before now)
        Calendar currentScheduleAlarm = null; //this will be a time in the past or now
        for (Schedule schedule : allSchedules) {
            Calendar nextAlarm = schedule.getNextAlarm();
            Calendar lastAlarm = schedule.getLastAlarm();
            if (lastAlarm == null | nextAlarm == null) continue;
            if (nextSchedule == null) {
                nextSchedule = schedule;
                nextScheduleAlarm = nextAlarm;
            }
            if (nextAlarm.before(nextScheduleAlarm)) {
                nextSchedule = schedule;
                nextScheduleAlarm = nextAlarm;
            }

            if (currentSchedule == null) {
                currentSchedule = schedule;
                currentScheduleAlarm = lastAlarm;
            }
            if (lastAlarm.after(currentScheduleAlarm)) {
                currentSchedule = schedule;
                currentScheduleAlarm = lastAlarm;
            }

        }
        if (currentSchedule == null | nextScheduleAlarm == null) {
            calculatedCurrentScheduledProfile = null;
            calculatedNextScheduledAlarm = null;
        } else {
            calculatedCurrentScheduledProfile = currentSchedule.getProfile();
            calculatedNextScheduledAlarm = nextScheduleAlarm;
        }
        lastCalculationRun = now;
    }

}