package com.sindhagro.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SindhagroWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH_WIDGET = "com.sindhagro.app.ACTION_REFRESH_WIDGET";
    private static final String PREFS_NAME = "com.sindhagro.app.widget_prefs";
    private static final String KEY_TIP_INDEX = "widget_tip_index";

    // Proverb / Agro Tip data structure
    private static class TipItem {
        final String saying;
        final String explanation;

        TipItem(String saying, String explanation) {
            this.saying = saying;
            this.explanation = explanation;
        }
    }

    // Authentic Sindhi agricultural proverbs and folk wisdom
    private static final String[][] FALLBACK_PROVERBS = {
        {"ڄيٺ جي جهل، پاڻيءَ جو مل.", "ڄيٺ ۾ گرمي وڌندي آهي، تنهنڪري ٻنيءَ جي آلاڻ تي نظر رکڻ گهرجي."},
        {"آکاڙ ۾ هَرَ هلائي، سو ئي انُ اپائي.", "وقت سر زمين کي هَرَ ڏئي تيار ڪرڻ سان فصل سٺو ۽ سرسبز ٿئي ٿو."},
        {"پاڻي گهٽ، ڀاڻ وڌيڪ؛ ٻني ٿئي سون سريڪ.", "پاڻي ۽ ڀاڻ جو متوازن استعمال فصل کي مضبوط بڻائي ٿو."},
        {"صبح جو ماڪ، ٻنيءَ لاءِ شفا پاڪ.", "ماڪ پوکن جي آلاڻ برقرار رکڻ ۾ انتهائي مددگار هوندي آهي."},
        {"سيٽلائيٽ آسمان ۾، ڳالهه هاريءَ جي زبان ۾.", "سنڌاڳڙو سيٽلائيٽ سان پنهنجي ٻنيءَ جي آلاڻ ۽ صحت چيڪ ڪريو."}
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH_WIDGET.equals(intent.getAction())) {
            // Cycle tip index in preferences
            cycleTipIndex(context);

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int singleId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (singleId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateAppWidget(context, appWidgetManager, singleId);
            } else {
                ComponentName thisWidget = new ComponentName(context, SindhagroWidgetProvider.class);
                int[] allIds = appWidgetManager.getAppWidgetIds(thisWidget);
                for (int id : allIds) {
                    updateAppWidget(context, appWidgetManager, id);
                }
            }
        }
    }

    private static void cycleTipIndex(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int current = prefs.getInt(KEY_TIP_INDEX, 0);
            prefs.edit().putInt(KEY_TIP_INDEX, current + 1).apply();
        } catch (Exception ignored) {
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sindhagro);

            // 1. Header Bar: App label, Location, Refresh Button
            views.setTextViewText(R.id.widget_app_label, "Sindhi-Agro");
            String location = resolveLocation(context);
            views.setTextViewText(R.id.widget_location, location);

            // Refresh action PendingIntent
            Intent refreshIntent = new Intent(context, SindhagroWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH_WIDGET);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    refreshIntent,
                    flags
            );
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent);

            // 2. Main Body: Sindhi agricultural month/season & rotating agro proverb/tip
            String seasonText = resolveCalendarSeason(context);
            views.setTextViewText(R.id.widget_calendar_season, seasonText);

            TipItem tip = resolveTip(context);
            views.setTextViewText(R.id.widget_tip_saying, "«" + tip.saying + "»");
            views.setTextViewText(R.id.widget_tip_explanation, tip.explanation);

            // 3. Footer Strip: Saved field summary & Open App PendingIntent
            String fieldSummary = resolveFieldSummary(context);
            views.setTextViewText(R.id.widget_field_status, fieldSummary);

            // Open MainActivity PendingIntent
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    flags
            );

            // Attach open intent to container and open button
            views.setOnClickPendingIntent(R.id.widget_container, openPendingIntent);
            views.setOnClickPendingIntent(R.id.widget_btn_open, openPendingIntent);

            // Update widget via AppWidgetManager
            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            // Safe fallback: widget host should never crash
        }
    }

    private static String resolveLocation(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String saved = prefs.getString("selected_district", null);
            if (saved == null || saved.trim().isEmpty()) {
                saved = prefs.getString("district", null);
            }
            if (saved == null || saved.trim().isEmpty()) {
                saved = prefs.getString("location", null);
            }
            if (saved != null && !saved.trim().isEmpty()) {
                return "📍 " + saved.trim();
            }

            // Read from sindh-admin.json
            String json = loadAssetString(context, "data/sindh-admin.json");
            if (json != null) {
                JSONObject root = new JSONObject(json);
                JSONArray divisions = root.optJSONArray("divisions");
                if (divisions != null && divisions.length() > 0) {
                    JSONObject div = divisions.optJSONObject(0);
                    if (div != null) {
                        JSONArray districts = div.optJSONArray("districts");
                        if (districts != null && districts.length() > 0) {
                            JSONObject dist = districts.optJSONObject(0);
                            if (dist != null) {
                                String name = dist.optString("name", "");
                                if (!name.isEmpty()) {
                                    return "📍 " + name;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "📍 حيدرآباد";
    }

    private static String resolveCalendarSeason(Context context) {
        try {
            String json = loadAssetString(context, "data/sindhi-calendar.json");
            if (json != null) {
                JSONObject root = new JSONObject(json);
                JSONObject today = root.optJSONObject("today");
                String sambat = today != null ? today.optString("sindhi_sambat", "") : "";
                String season = root.optString("season", "");
                if (season.isEmpty() && today != null) {
                    season = today.optString("season", "");
                }
                if (season.isEmpty()) {
                    season = "خريف جو مند";
                }
                if (!sambat.isEmpty()) {
                    return "📅 " + sambat + " · " + season;
                }
            }
        } catch (Exception ignored) {
        }
        return "📅 10 ڄيٺ 1400 · خريف جو مند";
    }

    private static TipItem resolveTip(Context context) {
        List<TipItem> tips = new ArrayList<>();

        // Load from folk-wisdom.json
        try {
            String json = loadAssetString(context, "data/folk-wisdom.json");
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        String saying = obj.optString("saying", "");
                        String explanation = obj.optString("explanation", "");
                        if (!saying.isEmpty()) {
                            tips.add(new TipItem(saying, explanation));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Add fallback proverbs
        for (String[] pair : FALLBACK_PROVERBS) {
            tips.add(new TipItem(pair[0], pair[1]));
        }

        // Determine current index from SharedPreferences
        int index = 0;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            index = prefs.getInt(KEY_TIP_INDEX, 0);
        } catch (Exception ignored) {
        }

        if (tips.isEmpty()) {
            return new TipItem("ڄيٺ جي جهل، پاڻيءَ جو مل.", "ڄيٺ ۾ گرمي وڌندي آهي، تنهنڪري ٻنيءَ جي آلاڻ تي نظر رکڻ گهرجي.");
        }

        int selectedIndex = Math.abs(index) % tips.size();
        return tips.get(selectedIndex);
    }

    private static String resolveFieldSummary(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String crop = prefs.getString("crop_name", null);
            String status = prefs.getString("field_status", null);
            if (status == null || status.trim().isEmpty()) {
                status = prefs.getString("irrigation_status", null);
            }
            if (status == null || status.trim().isEmpty()) {
                status = prefs.getString("field_summary", null);
            }

            // Read from widget-data.json
            if (status == null || status.trim().isEmpty()) {
                String json = loadAssetString(context, "widget-data.json");
                if (json != null) {
                    JSONObject root = new JSONObject(json);
                    status = root.optString("status", "");
                    if (status.isEmpty()) {
                        status = root.optString("subtitle", "");
                    }
                }
            }

            if (status == null || status.trim().isEmpty()) {
                status = "ٻني سرسبز آهي · آلاڻ مناسب";
            }

            if (crop != null && !crop.trim().isEmpty()) {
                return "🌾 " + crop.trim() + " · " + status.trim();
            }
            return "🌾 " + status.trim();
        } catch (Exception ignored) {
            return "🌾 ٻني سرسبز آهي · آلاڻ مناسب";
        }
    }

    private static String loadAssetString(Context context, String assetPath) {
        try (InputStream is = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
