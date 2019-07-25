package fr.neamar.kiss.shortcut;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.DataHandler;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.dataprovider.ShortcutsProvider;
import fr.neamar.kiss.pojo.ShortcutsPojo;

public class UninstallShortcutHandler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent data) {

        DataHandler dh = KissApplication.getApplication(context).getDataHandler();
        ShortcutsProvider sp = dh.getShortcutsProvider();

        if (sp == null)
            return;

        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (BuildConfig.DEBUG) Log.w("onReceive", "Uninstall shortcut " + name);

        ShortcutsPojo pojo = (ShortcutsPojo) sp.findByName(name);
        if (pojo == null) {
            if (BuildConfig.DEBUG) Log.w("onReceive", "Shortcut " + name + " not found");
            return;
        }

        dh.removeShortcut(pojo);

    }

}