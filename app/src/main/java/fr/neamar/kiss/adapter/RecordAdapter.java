package fr.neamar.kiss.adapter;

import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SectionIndexer;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.normalizer.StringNormalizer;
import fr.neamar.kiss.result.AppResult;
import fr.neamar.kiss.result.ContactsResult;
import fr.neamar.kiss.result.PhoneResult;
import fr.neamar.kiss.result.Result;
import fr.neamar.kiss.result.SearchResult;
import fr.neamar.kiss.result.SettingsResult;
import fr.neamar.kiss.result.ShortcutsResult;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.ui.ListPopup;
import fr.neamar.kiss.utils.FuzzyScore;

public class RecordAdapter extends BaseAdapter implements SectionIndexer {
    private final Context context;
    private final QueryInterface parent;
    private FuzzyScore fuzzyScore;
    private static final String TAG = RecordAdapter.class.getSimpleName();
    /**
     * Array list containing all the results currently displayed
     */
    private List<Result> results;

    // Mapping from letter to a position (only used for fast scroll, when viewing app list)
    private final HashMap<String, Integer> alphaIndexer = new HashMap<>();
    // List of available sections (only used for fast scroll)
    private String[] sections = new String[0];

    public RecordAdapter(Context context, QueryInterface parent, ArrayList<Result> results) {
        this.context = context;
        this.parent = parent;
        this.results = results;
        this.fuzzyScore = null;
    }

    @Override
    public int getViewTypeCount() {
        return 6;
    }

    @Override
    public int getItemViewType(int position) {
        if (results.get(position) instanceof AppResult)
            return 0;
        else if (results.get(position) instanceof SearchResult)
            return 1;
        else if (results.get(position) instanceof ContactsResult)
            return 2;
        else if (results.get(position) instanceof SettingsResult)
            return 3;
        else if (results.get(position) instanceof PhoneResult)
            return 4;
        else if (results.get(position) instanceof ShortcutsResult)
            return 5;
        else
            return -1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getCount() {
        return results.size();
    }

    @Override
    public Object getItem(int position) {
        return results.get(position);
    }

    @Override
    public long getItemId(int position) {
        // In some situation, Android tries to display an item that does not exist (e.g. item 24 in a list containing 22 items)
        // See https://github.com/Neamar/KISS/issues/890
        return position < results.size() ? results.get(position).getUniqueId() : -1;
    }

    @Override
    public @NonNull
    View getView(int position, View convertView, @NonNull ViewGroup parent) {
        return results.get(position).display(parent.getContext(), convertView, parent, fuzzyScore);
    }


    public void onLongClick(final int pos, View v) {
        ListPopup menu = results.get(pos).getPopupMenu(v.getContext(), this, v);

        //check if menu contains elements and if yes show it
        if (menu.getAdapter().getCount() > 0) {
            parent.registerPopup(menu);
            menu.show(v);
        }
    }

    public void onClick(final int position, View v) {
        final Result result;

        try {
            result = results.get(position);
            result.launch(v.getContext(), v);
        } catch (ArrayIndexOutOfBoundsException ignored) {
            return;
        }

        // Record the launch after some period,
        // * to ensure the animation runs smoothly
        // * to avoid a flickering -- launchOccurred will refresh the list
        // Thus TOUCH_DELAY * 3
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                parent.launchOccurred();
            }
        }, KissApplication.TOUCH_DELAY * 3);

    }

    public void removeResult(Context context, Result result) {
        results.remove(result);
        result.deleteRecord(context);
        notifyDataSetChanged();
    }

    public void updateResults(List<Result> results, String query) {
        this.results = results;
        StringNormalizer.Result queryNormalized = StringNormalizer.normalizeWithResult(query, false);

        fuzzyScore = new FuzzyScore(queryNormalized.codePoints, true);
        notifyDataSetChanged();
    }

    /**
     * Force set transcript mode on the list.
     * Prefer to use `parent.temporarilyDisableTranscriptMode();`
     */
    public void updateTranscriptMode(int transcriptMode) {
        parent.updateTranscriptMode(transcriptMode);
    }

    public void clear() {
        this.results.clear();
        notifyDataSetChanged();
    }

    /**
     * When using fast scroll, generate a mapping to know where a given letter starts in the list
     * (can only be used with a sorted result set!)
     */
    public void buildSections(boolean contacts) {
        alphaIndexer.clear();
        int size = results.size();

        // Generate the mapping letter => number
        for (int x = 0; x < size; x++) {
            String s = results.get(x).getSection();

            // Put the first one
            if (!alphaIndexer.containsKey(s)) {
                alphaIndexer.put(s, x);
            }
        }

        // Generate section list
        Set<String> sectionLetters = alphaIndexer.keySet();
        ArrayList<String> sectionList = new ArrayList<>(sectionLetters);
        Collections.sort(sectionList);
        // We're displaying from A to Z, everything needs to be reversed
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Apply app sorting preference
        if (!contacts) {
            if (prefs.getString("sort-apps", "alphabetical").equals("invertedAlphabetical")) {
                Collections.reverse(sectionList);
            }
        } else {
            if (prefs.getString("sort-contacts", "alphabetical").equals("invertedAlphabetical")) {
                Collections.reverse(sectionList);
            }
        }

        sections = new String[sectionList.size()];
        sectionList.toArray(sections);
    }


    @Override
    public Object[] getSections() {
        return sections;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {

        if (sectionIndex > 0) {
            try {
                return alphaIndexer.get(sections[sectionIndex]);
            } catch (ArrayIndexOutOfBoundsException e){
                e.printStackTrace();
                return 0;
            }
        } else {
            return 0;
        }

    }

    @Override
    public int getSectionForPosition(int position) {
        for (int i = 0; i < sections.length; i++) {
            if (alphaIndexer.get(sections[i]) > position) {
                return i - 1;
            }
        }

        // If apps starting with the letter "A" cover more than a full screen,
        // we will never get > position
        // so we just return the before-last section
        // See #1005
        return sections.length - 2;
    }
    public void showDialog(DialogFragment dialog) {
        parent.showDialog(dialog);
    }
}
