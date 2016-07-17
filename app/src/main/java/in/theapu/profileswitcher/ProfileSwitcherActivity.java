package in.theapu.profileswitcher;


import android.Manifest;
import android.app.FragmentManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.view.View;
import java.util.Calendar;
import java.util.List;
import android.app.DialogFragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.support.v4.content.ContextCompat;

import com.github.clans.fab.FloatingActionButton;

import in.theapu.profileswitcher.R;

public class ProfileSwitcherActivity extends AppCompatActivity {

    private static final String TAG = "ProfileSwitcherActivity";
    private ProfileSwitcher profileSwitcher;
    private FloatingActionButton fab1,fab2;
    private ScheduleArrayAdapter adapter;
    private ScheduleDataSource datasource;
    private static final int DELETE_ID = Menu.FIRST + 1;
    private static final TextView TextView = null;
    private TextView currentProfileView = TextView;
    private TextView caculatedCurrentProfileView = TextView;
    private TextView nextScheduledChangeView = TextView;
    private TextView timedProfileView = TextView;
    boolean systemwritepermission = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_switcher);

        datasource = new ScheduleDataSource(this);
        datasource.open();
        profileSwitcher = new ProfileSwitcher(this);

        currentProfileView = (TextView) findViewById(R.id.currentProfile);
        caculatedCurrentProfileView = (TextView) findViewById(R.id.caculatedCurrentProfile);
        nextScheduledChangeView = (TextView) findViewById(R.id.nextScheduledChange);
        timedProfileView = (TextView) findViewById(R.id.timedProfile);

        fab1 = (FloatingActionButton)findViewById(R.id.fab1);
        fab2 = (FloatingActionButton)findViewById(R.id.fab2);

        final int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS);

        fab1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), ScheduleActivity.class);
                startActivity(i);
            }
        });

        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timedProfile();
            }
        });

        showAllSchedules();

        systemwritepermission =  Settings.System.canWrite(this);

        if(!systemwritepermission){
            FragmentManager fm = getFragmentManager();
            PopupWritePermission dialogFragment = new PopupWritePermission();
            dialogFragment.show(fm, getString(R.string.popup_writesettings_title));
        }
    }



    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_profile_switcher, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.debug_mode);
        if (profileSwitcher.isDebugEnabled() ) {
            menuItem.setTitle("Turn Off Debug Mode");
        } else {
            menuItem.setTitle("Turn On Debug Mode");
        }
        menuItem = menu.findItem(R.id.test_alarm);
        menuItem.setVisible(profileSwitcher.isDebugEnabled());

        menuItem = menu.findItem(R.id.reset_on_headset_plug);
        if (profileSwitcher.resetOnHeadsetPlug() ) {
            menuItem.setTitle("Headset Plug: Reset Profile");
        } else {
            menuItem.setTitle("Headset Plug: Do Nothing");
        }
        menuItem = menu.findItem(R.id.reset_on_headset_unplug);
        if (profileSwitcher.resetOnHeadsetUnplug() ) {
            menuItem.setTitle("Headset Unplug: Reset Profile");
        } else {
            menuItem.setTitle("Headset Unplug: Do Nothing");
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.about:
                displayAbout();
                return true;
            case R.id.manage_profiles:
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                return true;
            case R.id.test_alarm:
                Calendar testCal = Calendar.getInstance();
                testCal.add(Calendar.SECOND, 10);
                profileSwitcher.setAlarm(testCal, ProfileSwitcher.REGULAR_ALARM);
                return true;
            case R.id.debug_mode:
                profileSwitcher.toggleDebugMode();
                return true;
            case R.id.reset_on_headset_plug:
                profileSwitcher.toggleResetOnHeadsetPlug();
                return true;
            case R.id.reset_on_headset_unplug:
                profileSwitcher.toggleResetOnHeadsetUnplug();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case DELETE_ID:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                        .getMenuInfo();
                Log.v(TAG,"in onContextItemSelected "+ info.toString());
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        Log.v(TAG,"SPSP ???? in onActivityResult");
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        Log.v(TAG,"SPSP ???? in onCreateContextMenu");
    }

    @Override
    protected void onResume() {
        Log.v(TAG,"in onResume");
        datasource.open();
        profileSwitcher.signalPossibleChangesMade();
        showAllSchedules();
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.v(TAG,"in onPause");
        Log.v(TAG,"SPSP in onPause");
        datasource.close();
        super.onPause();
    }

    void  showAllSchedules() {
        currentProfileView.setText(profileSwitcher.getActiveProfileName());
        caculatedCurrentProfileView.setText(profileSwitcher.getProfileToChangeTo());
        nextScheduledChangeView.setText(ProfileSwitcher.calendarToString(profileSwitcher.getNextScheduledAlarm()));
        Calendar timedProfileExires = profileSwitcher.getTimedProfileExires();
        if (timedProfileExires != null) {
            timedProfileView.setText("expires on " + ProfileSwitcher.calendarToEHHmm(timedProfileExires));
        } else {
            timedProfileView.setText("<none>");
        }

        List<Schedule> allSchedules;
        allSchedules = datasource.getAllSchedules();

        ListView scheduleList = (ListView) findViewById(R.id.schedulelist);
        adapter = new ScheduleArrayAdapter(this, allSchedules);
        scheduleList.setAdapter(adapter);


        scheduleList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapter, View view,
                                    int position, long id) {
                Log.v(TAG, "in onItemClick");
                Schedule schedule = (Schedule) adapter.getItemAtPosition(position);
                Intent i = new Intent(getApplicationContext(), ScheduleActivity.class);
                i.putExtra(ScheduleActivity.EXTRAS_SCHEDULE_ID, schedule.getId());
                startActivity(i);

            }
        });

    }

    private void timedProfile() {
        DialogFragment newFragment = TimedProfileFragment.newInstance(this);
        newFragment.show(getFragmentManager(), "dialog");
    }

    private void displayAbout(){
        DialogFragment newFragment = AboutFragment.newInstance(this);
        newFragment.show(getFragmentManager(), "dialog");
    }

}
