package in.theapu.profileswitcher;

import android.app.DialogFragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * Created by apu on 17/7/16.
 */

public class PopupWritePermission extends DialogFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_permission_request_dialog, container, false);
        getDialog().setTitle("Simple Dialog");
        Button dismiss = (Button) rootView.findViewById(R.id.dismiss);
        dismiss.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dismiss();
                Intent grantIntent = new  Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                grantIntent.setData(Uri.parse("package:in.theapu.profileswitcher"));
                startActivity(grantIntent);
            }
        });

        return rootView;
    }
}