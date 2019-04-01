package fr.neamar.kiss;

import android.content.Context;
import android.util.Log;

import java.util.Map;

import fr.neamar.kiss.db.DBHelper;

public class BadgeHandler {
    private static final String TAG = BadgeHandler.class.getSimpleName();
    Context context;
    //cached badges
    private Map<String, Integer> badgeCache;

    public BadgeHandler(Context context) {
        this.context = context;
        badgeCache = DBHelper.loadBadges(this.context);
    }

    public Integer getBadgeCount(String packageName){
        Integer badgeCount = badgeCache.get(packageName);
        if (badgeCount == null) {
            return 0;
        }
        if (badgeCount>0){
            Log.d(TAG,"packageName:"+packageName+" badges:"+badgeCount);
        }
        return badgeCount;
    }


    public void setBadgeCount(String packageName, Integer badge_count) {
        //upsert badge count on the db
        DBHelper.setBadgeCount(this.context, packageName, badge_count);
        //add to cache
        badgeCache.put(packageName, badge_count);
    }
}