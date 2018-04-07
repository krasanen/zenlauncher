package fr.neamar.kiss.preference;

import android.content.Context;

import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;


import fi.zmengames.zlauncher.LauncherService;
import fi.zmengames.zlauncher.ZEvent;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.SwitchPreference;

public class SignInSwitch extends SwitchPreference {
    public SignInSwitch(Context context) {
        this(context, null);
    }

    public SignInSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.switchPreferenceStyle);
    }

    public SignInSwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }



    @Override
    protected void onClick() {
        Log.d("SignInSwitch","isChecked:"+isChecked());

        if (!isChecked()){
            Log.d("SignInSwitch","SIGN_IN2");
            Intent intent = new Intent(getContext(), LauncherService.class);
            intent.setAction(LauncherService.GOOGLE_SIGN_IN);
            getContext().startService(intent);

        } else {
            Log.d("SignInSwitch","SIGN_OUT2");
            Intent intent = new Intent(getContext(), LauncherService.class);
            intent.setAction(LauncherService.GOOGLE_SIGN_OUT);
            getContext().startService(intent);
        }
        super.onClick();

    }
}
