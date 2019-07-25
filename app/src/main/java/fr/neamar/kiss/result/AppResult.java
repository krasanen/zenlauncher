package fr.neamar.kiss.result;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import fi.zmengames.zen.Utility;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import fi.zmengames.zen.ZEvent;
import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.cache.MemoryCacheHelper;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.ui.ListPopup;
import fr.neamar.kiss.utils.FuzzyScore;
import fr.neamar.kiss.utils.SpaceTokenizer;
import me.leolin.shortcutbadger.ShortcutBadgeException;
import me.leolin.shortcutbadger.impl.IntentConstants;
import me.leolin.shortcutbadger.util.BroadcastHelper;

import static org.greenrobot.eventbus.EventBus.TAG;

public class AppResult extends Result {
    private final AppPojo appPojo;
    private final ComponentName className;
    private Drawable icon = null;
    private PopupMenu popupExcludeMenu;

    AppResult(AppPojo appPojo) {
        super(appPojo);
        this.appPojo = appPojo;

        className = new ComponentName(appPojo.packageName, appPojo.activityName);
    }

    @Override
    public View display(final Context context, int position, View convertView, FuzzyScore fuzzyScore) {
        View view = convertView;
        if (convertView == null) {
            view = inflateFromId(context, R.layout.item_app);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        TextView appName = view.findViewById(R.id.item_app_name);

        displayHighlighted(appPojo.normalizedName, appPojo.getName(), fuzzyScore, appName, context);

        TextView tagsView = view.findViewById(R.id.item_app_tag);
        // Hide tags view if tags are empty
        if (appPojo.getTags().isEmpty()) {
            tagsView.setVisibility(View.GONE);
        } else if (displayHighlighted(appPojo.getNormalizedTags(), appPojo.getTags(),
                fuzzyScore, tagsView, context) || prefs.getBoolean("tags-visible", true)) {
            tagsView.setVisibility(View.VISIBLE);
        } else {
            tagsView.setVisibility(View.GONE);
        }

        final ImageView appIcon = view.findViewById(R.id.item_app_icon);
        if (!prefs.getBoolean("icons-hide", false)) {
            this.setAsyncDrawable(appIcon);
        } else {
            appIcon.setImageDrawable(null);
        }
        TextView badgeView = (TextView) view.findViewById(R.id.item_badge_count);
        if (pojo.getBadgeCount() > 0) {
            badgeView.setText(String.valueOf(pojo.getBadgeText()));
            badgeView.setVisibility(View.VISIBLE);
        } else {
            badgeView.setVisibility(View.GONE);
        }
        return view;
    }

    private static final String INTENT_ACTION = IntentConstants.DEFAULT_INTENT_ACTION;
    private static final String INTENT_EXTRA_BADGE_COUNT = "badge_count";
    private static final String INTENT_EXTRA_PACKAGENAME = "badge_count_package_name";
    private static final String INTENT_EXTRA_ACTIVITY_NAME = "badge_count_class_name";

    public static void executeBadge(Context context, String packageName, String className, int badgeCount) throws ShortcutBadgeException {
        if (BuildConfig.DEBUG) Log.i(TAG, "executeBadge " + packageName + ":" + className + "badgeCount:"+badgeCount);
        Intent intent = new Intent(INTENT_ACTION);
        intent.putExtra(INTENT_EXTRA_BADGE_COUNT, badgeCount);
        intent.putExtra(INTENT_EXTRA_PACKAGENAME, packageName);
        intent.putExtra(INTENT_EXTRA_ACTIVITY_NAME, className);

        BroadcastHelper.sendDefaultIntentExplicitly(context, intent);
        KissApplication.getApplication(context).getDataHandler().getBadgeHandler().setBadgeCount(packageName, badgeCount);
        ZEvent event = new ZEvent(ZEvent.State.BADGE_COUNT);
        EventBus.getDefault().post(event);
    }

    @Override
    protected ListPopup buildPopupMenu(Context context, ArrayAdapter<ListPopup.Item> adapter, final RecordAdapter parent, View parentView) {
        if ((!(context instanceof MainActivity)) || (((MainActivity) context).isViewingSearchResults())) {
            adapter.add(new ListPopup.Item(context, R.string.menu_remove));
        }
        adapter.add(new ListPopup.Item(context, R.string.menu_exclude));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_add));
        adapter.add(new ListPopup.Item(context, R.string.menu_tags_edit));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_remove));
        adapter.add(new ListPopup.Item(context, R.string.menu_app_details));
        adapter.add(new ListPopup.Item(context, R.string.add_badge));
        if (appPojo.getBadgeCount()>0) {
            adapter.add(new ListPopup.Item(context, R.string.removeBadge));
        }

        try {
            // app installed under /system can't be uninstalled
            boolean isSameProfile = true;
            ApplicationInfo ai;
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LauncherApps launcher = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                LauncherActivityInfo info = launcher.getActivityList(this.appPojo.packageName, this.appPojo.userHandle.getRealHandle()).get(0);
                ai = info.getApplicationInfo();

                isSameProfile = this.appPojo.userHandle.isCurrentUser();
            } else {
                ai = context.getPackageManager().getApplicationInfo(this.appPojo.packageName, 0);
            }

            // Need to AND the flags with SYSTEM:
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && isSameProfile) {
                adapter.add(new ListPopup.Item(context, R.string.menu_app_uninstall));
            }
        } catch (NameNotFoundException | IndexOutOfBoundsException e) {
            // should not happen
        }

        //append root menu if available
        if (KissApplication.getApplication(context).getRootHandler().isRootActivated() && KissApplication.getApplication(context).getRootHandler().isRootAvailable()) {
            adapter.add(new ListPopup.Item(context, R.string.menu_app_hibernate));
        }

        return inflatePopupMenu(adapter, context);
    }

    @Override
    protected boolean popupMenuClickHandler(final Context context, final RecordAdapter parent, int stringId, View parentView) {
        switch (stringId) {
            case R.string.menu_app_details:
                launchAppDetails(context, appPojo);
                return true;
            case R.string.menu_app_uninstall:
                launchUninstall(context, appPojo);
                return true;
            case R.string.menu_app_hibernate:
                hibernate(context, appPojo);
                return true;
            case R.string.menu_exclude:

                final int EXCLUDE_HISTORY_ID = 0;
                final int EXCLUDE_KISS_ID = 1;
                PopupMenu popupExcludeMenu = new PopupMenu(context, parentView);
                //Adding menu items
                popupExcludeMenu.getMenu().add(EXCLUDE_HISTORY_ID, Menu.NONE, Menu.NONE, R.string.menu_exclude_history);
                popupExcludeMenu.getMenu().add(EXCLUDE_KISS_ID, Menu.NONE, Menu.NONE, R.string.menu_exclude_kiss);
                //registering popup with OnMenuItemClickListener
                popupExcludeMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getGroupId()) {
                            case EXCLUDE_HISTORY_ID:
                                excludeFromHistory(context, appPojo, parent);
                                return true;
                            case EXCLUDE_KISS_ID:
                                // remove item since it will be hidden
                                parent.removeResult(AppResult.this);
                                excludeFromKiss(context, appPojo);
                                return true;
                        }

                        return true;
                    }
                });
                Utility.showPopup(popupExcludeMenu, KissApplication.getApplication(context).getMainActivity());
                return true;
            case R.string.menu_tags_edit:
                launchEditTagsDialog(context, appPojo);
                return true;
            case R.string.add_badge:

                try {
                    executeBadge(context, appPojo.packageName, appPojo.activityName, appPojo.getBadgeCount()+1);
                } catch (ShortcutBadgeException e) {
                    e.printStackTrace();
                }

                return true;
            case R.string.removeBadge:

                try {
                    executeBadge(context, appPojo.packageName, appPojo.activityName, 0);
                } catch (ShortcutBadgeException e) {
                    e.printStackTrace();
                }

                return true;
        }

        return super.popupMenuClickHandler(context, parent, stringId, parentView);
    }

    static int badgecount = 0;

    private void excludeFromHistory(Context context, AppPojo appPojo, final RecordAdapter parent) {
        //add to excluded from history app list
        KissApplication.getApplication(context).getDataHandler().addToExcludedFromHistory(appPojo);
        //remove from history
        deleteRecord(context);
        //refresh current history
        parent.removeResult(AppResult.this);
        //inform user
        Toast.makeText(context, R.string.excluded_app_history_added, Toast.LENGTH_LONG).show();
    }

    private void excludeFromKiss(Context context, AppPojo appPojo) {
        KissApplication.getApplication(context).getDataHandler().addToExcluded(appPojo);
        //remove app pojo from appProvider results - no need to reset handler
        KissApplication.getApplication(context).getDataHandler().getAppProvider().removeApp(appPojo);
        KissApplication.getApplication(context).getDataHandler().removeFromFavorites(KissApplication.getApplication(context).getMainActivity(), appPojo.id);
        Toast.makeText(context, R.string.excluded_app_list_added, Toast.LENGTH_LONG).show();
    }


    private void launchEditTagsDialog(final Context context, final AppPojo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getResources().getString(R.string.tags_add_title));

        // Create the tag dialog
        final View v = View.inflate(context, R.layout.tags_dialog, null);
        final MultiAutoCompleteTextView tagInput = v.findViewById(R.id.tag_input);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, KissApplication.getApplication(context).getDataHandler().getTagsHandler().getAllTagsAsArray());
        tagInput.setTokenizer(new SpaceTokenizer());
        tagInput.setText(appPojo.getTags());

        tagInput.setAdapter(adapter);
        builder.setView(v);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                // Refresh tags for given app
                app.setTags(tagInput.getText().toString());
                KissApplication.getApplication(context).getDataHandler().getTagsHandler().setTags(app.id, app.getTags());
                // Show toast message
                String msg = context.getResources().getString(R.string.tags_confirmation_added);
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        dialog.show();
    }

    /**
     * Open an activity displaying details regarding the current package
     */
    private void launchAppDetails(Context context, AppPojo app) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LauncherApps launcher = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            assert launcher != null;
            launcher.startAppDetailsActivity(className, appPojo.userHandle.getRealHandle(), null, null);
        } else {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", app.packageName, null));
            context.startActivity(intent);
        }
    }

    private void hibernate(Context context, AppPojo app) {
        String msg = context.getResources().getString(R.string.toast_hibernate_completed);
        if (!KissApplication.getApplication(context).getRootHandler().hibernateApp(appPojo.packageName)) {
            msg = context.getResources().getString(R.string.toast_hibernate_error);
        }

        Toast.makeText(context, String.format(msg, app.getName()), Toast.LENGTH_SHORT).show();
    }

    /**
     * Open an activity to uninstall the current package
     */
    private void launchUninstall(Context context, AppPojo app) {
        Intent intent = new Intent(Intent.ACTION_DELETE,
                Uri.fromParts("package", app.packageName, null));
        context.startActivity(intent);
    }

    @Override
    boolean isDrawableCached() {
        return icon != null;
    }

    @Override
    void setDrawableCache(Drawable drawable) {
        icon = drawable;
    }

    @Override
    public Drawable getDrawable(Context context) {
        synchronized (this) {
            if (icon == null) {
                icon = MemoryCacheHelper.getAppIconDrawable(context, className, this.appPojo.userHandle);
            }

            return icon;
        }
    }

    @Override
    public void doLaunch(Context context, View v) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // release camera to be usable in other apps
            KissApplication.getApplication(context).getMainActivity().toggleFlashLightPreM(false);
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LauncherApps launcher = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                assert launcher != null;
                Rect sourceBounds = null;
                Bundle opts =null;

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // We're on a modern Android and can display activity animations
                    // If AppResult, find the icon
                    View potentialIcon = v.findViewById(R.id.item_app_icon);
                    if(potentialIcon == null) {
                        // If favorite, find the icon
                        potentialIcon = v.findViewById(R.id.favorite);
                    }

                    if (potentialIcon != null) {
                        // If we got an icon, we create options to get a nice animation
                        opts = ActivityOptions.makeClipRevealAnimation(potentialIcon, 0, 0, potentialIcon.getMeasuredWidth(), potentialIcon.getMeasuredHeight()).toBundle();
                    }
                }

                launcher.startMainActivity(className, appPojo.userHandle.getRealHandle(), sourceBounds, opts);
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(className);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.setSourceBounds(v.getClipBounds());
                }

                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException | NullPointerException e) {
            // Application was just removed?
            // (null pointer exception can be thrown on Lollipop+ when app is missing)
            Toast.makeText(context, R.string.application_not_found, Toast.LENGTH_LONG).show();
        }
    }
    public String getPackageName() {
        return appPojo.packageName;
    }
}
