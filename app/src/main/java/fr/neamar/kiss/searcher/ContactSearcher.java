package fr.neamar.kiss.searcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.PojoComparator;

/**
 * Returns the list of all applications on the system
 */
public class ContactSearcher extends Searcher {
    public ContactSearcher(MainActivity activity) {
        super(activity, "<application>");
    }
    public static Bitmap whatsAppIcon;
    public static Bitmap signalIcon;
    public static Bitmap facebookIcon;
    @Override
    PriorityQueue<Pojo> getPojoProcessor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Apply app sorting preference
        if (prefs.getString("sort-contacts", "alphabetical").equals("alphabetical")) {
            return new PriorityQueue<>(DEFAULT_MAX_RESULTS, new PojoComparator());
        } else {
            return new PriorityQueue<>(DEFAULT_MAX_RESULTS, Collections.reverseOrder(new PojoComparator()));
        }
    }
    @Override
    protected int getMaxResultCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        MainActivity activity = activityWeakReference.get();
        if (activity == null)
            return null;

        List<Pojo> pojos = KissApplication.getApplication(activity).getDataHandler().getContactsProvider().getAllContacts();
        this.addResult(pojos.toArray(new Pojo[0]));
        return null;
    }

    @Override
    protected void onPostExecute(Void param) {
        super.onPostExecute(param);
        // Build sections for fast scrolling
        activityWeakReference.get().adapter.buildSections(true);
    }
}
