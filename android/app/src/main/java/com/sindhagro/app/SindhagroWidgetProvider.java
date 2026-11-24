package com.sindhagro.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

public class SindhagroWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH_WIDGET = "com.sindhagro.app.ACTION_REFRESH_WIDGET";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Sindhi month name ↔ Gregorian month mapping.
    // Sindhi Sambat months map approximately to pairs of Gregorian months.
    // We pick the month name based on the Gregorian calendar month so that
    // the widget works without a Sindhi calendar engine.
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private static final String[] SINDHI_MONTHS_BY_GREGORIAN = {
        // Jan=0  Feb=1   Mar=2   Apr=3   May=4   Jun=5
           "پوهه","ماگهه","ڦگڻ", "چيت", "ويساک","ڄيٺ",
        // Jul=6   Aug=7   Sep=8   Oct=9   Nov=10  Dec=11
           "آساڙ","سرواڻ","ڀادون","آسو", "ڪتي", "مگهر"
    };

    // Built-in fallback data matching folk-wisdom.json structure.
    // Keys: Sindhi month name (same as "month" field in JSON).
    private static final String[][] MONTH_DATA = {
        {"چيت",   "وسنت (بهار)",      "چيت جي چانڊي، پوک جي وانڊي.",              "چيت ۾ موسم نرم ٿيندو آهي — هي پوک لاءِ تياريءَ جو مهينو آهي.",                              "🌱 ٽماٽا · مرچون · کاٻو پٽ · ڀاڄيون · ڪپهه"},
        {"ويساک", "وسنت (بهار آخر)", "ويساک ۾ ٻج ڀاڙو، سال ڀر کاڌو پاڙو.",       "ويساک ۾ فصل جي پوک ڪرڻ سال ڀر جي خوراڪ جي ضمانت آهي.",                                      "🌱 ڪپهه · ٻاجهري · مڱي · ڀاڄيون · پياز"},
        {"ڄيٺ",   "گرما",             "ڄيٺ جي جهل، پاڻيءَ جو مل.",                "ڄيٺ ۾ گرمي وڌندي آهي، تنهنڪري ٻنيءَ جي آلاڻ تي نظر رکڻ گهرجي.",                            "🌱 ڪپهه · ڪکيارو · مونگ · ٿر ماٺي"},
        {"آساڙ",  "برسات",            "آساڙ جو مينهن، ڪسان جو چينهن.",            "آساڙ ۾ مينهن ايندو آهي، جيڪو فصلن لاءِ قدرتي نعمت آهي.",                                    "🌱 ٻاجهري · ڪک · مڱي · چاول · تل"},
        {"سرواڻ", "برسات",            "سرواڻ جو پاڻي، هاري جي ڪماڻي.",            "سرواڻ ۾ مينهن جو پاڻي فصل کي پروان چاڙهيندو آهي.",                                           "🌱 چاول · ڪک · تل · مونگ · ڦلّي"},
        {"ڀادون", "برسات آخر",       "ڀادون جي ڀاريءَ ۾، هاري جي خوشياريءَ ۾.", "ڀادون ۾ فصل بيهندو آهي، هاري کي خيال رکڻو آهي.",                                             "🌱 مونگ · ڦلّي · ڪک · مٺي سرسون"},
        {"آسو",   "خريف",             "آسو ۾ هَرَ هلائي، ربيع جو فصل اپائي.",     "آسو ۾ ربيع جي فصل لاءِ زمين تيار ڪجي.",                                                       "🌱 ڪڻڪ (تياري) · سرسون · ميتي · پالک"},
        {"ڪتي",   "خريف / ربيع آغاز","ڪتي ۾ ٻج کٽيو، رتي رتي لٽيو.",              "ڪتي ۾ وقت تي ٻج ڇٽڻ سان فصل ثمر ڏيندو آهي.",                                                "🌱 ڪڻڪ · جئون · سرسون · چڻا · ميتي"},
        {"مگهر",  "سياري",            "مگهر جي سياري، فصل جو بهاري.",              "مگهر ۾ ٿڌ شروع ٿيندي آهي — ربيع فصل هاڻي نشو ونما ڪري ٿو.",                                 "🌱 ڪڻڪ · جئون · چڻا · سرسون · آلو"},
        {"پوهه",  "سياري (سرد)",     "پوهه جي پالي، فصل جي والي.",                "پوهه ۾ ڪنوار پالڻ فصل کي نقصان کان بچائي ٿو.",                                               "🌱 ميتي · پالک · مٺي · مولي · گاجر"},
        {"ماگهه", "سياري آخر",       "ماگهه ۾ مينهن وسي، ڪسان جي کيسي.",         "ماگهه جو مينهن سياري جي فصل لاءِ تمام گهڻو فائدو ڏيندو آهي.",                               "🌱 آلو · پياز · ٽماٽا (آغاز) · ميتي"},
        {"ڦگڻ",   "وسنت آغاز",       "ڦگڻ ۾ گل کليو، هاريءَ جو دل کليو.",        "ڦگڻ ۾ موسم بهتر ٿيندو آهي، نئين پوک لاءِ وقت آهي.",                                         "🌱 ڀاڄيون · ٽماٽا · مرچون · کاٻو پٽ · ڪپهه (تياري)"}
    };
    // Index positions in MONTH_DATA row: 0=month, 1=season, 2=saying, 3=explanation, 4=plant_advice

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
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int singleId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (singleId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateAppWidget(context, appWidgetManager, singleId);
            } else {
                ComponentName thisWidget = new ComponentName(context, SindhagroWidgetProvider.class);
                for (int id : appWidgetManager.getAppWidgetIds(thisWidget)) {
                    updateAppWidget(context, appWidgetManager, id);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sindhagro);

            // 1. Determine current Sindhi month from device calendar
            String sindhiMonth = currentSindhiMonth();

            // 2. Load month-specific data (folk-wisdom.json → fallback to built-in table)
            String[] monthData = loadMonthData(context, sindhiMonth);
            String season      = monthData[0];
            String saying      = monthData[1];
            String explanation = monthData[2];
            String plantAdvice = monthData[3];

            // 3. Build month+season label (all Sindhi)
            String monthSeason = "📅 " + sindhiMonth + " · " + season;

            // 4. Set RemoteViews text
            views.setTextViewText(R.id.widget_app_label, context.getString(R.string.app_name));
            views.setTextViewText(R.id.widget_month_season,    monthSeason);
            views.setTextViewText(R.id.widget_tip_saying,      "« " + saying + " »");
            views.setTextViewText(R.id.widget_tip_explanation, explanation);
            views.setTextViewText(R.id.widget_plant_advice,    plantAdvice);

            // 5. Refresh PendingIntent → cycles the widget data on tap
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            Intent refreshIntent = new Intent(context, SindhagroWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH_WIDGET);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent refreshPI = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, flags);
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPI);

            // 6. Open-app PendingIntent → tapping container or button launches MainActivity
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openPI = PendingIntent.getActivity(context, 0, openIntent, flags);
            views.setOnClickPendingIntent(R.id.widget_container, openPI);
            views.setOnClickPendingIntent(R.id.widget_btn_open, openPI);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception ignored) {
            // Widget host must never crash
        }
    }

    // ─────────────────────────────────────────────────────────
    /** Returns the Sindhi month name for today using the device's Gregorian month. */
    private static String currentSindhiMonth() {
        try {
            int gregorianMonth = Calendar.getInstance().get(Calendar.MONTH); // 0-based
            if (gregorianMonth >= 0 && gregorianMonth < SINDHI_MONTHS_BY_GREGORIAN.length) {
                return SINDHI_MONTHS_BY_GREGORIAN[gregorianMonth];
            }
        } catch (Exception ignored) {}
        return "ڄيٺ"; // safe fallback
    }

    /**
     * Looks up folk-wisdom.json for a matching "month" key.
     * Falls back to built-in MONTH_DATA if file is missing or key not found.
     * Returns String[4]: {season, saying, explanation, plant_advice}
     */
    private static String[] loadMonthData(Context context, String targetMonth) {
        // Try JSON file first
        try {
            String raw = loadAssetString(context, "data/folk-wisdom.json");
            if (raw != null) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String month = obj.optString("month", "");
                    if (month.equals(targetMonth)) {
                        String season      = obj.optString("season", "");
                        String saying      = obj.optString("saying", "");
                        String explanation = obj.optString("explanation", "");
                        String plantAdvice = obj.optString("plant_advice", "");
                        if (!saying.isEmpty()) {
                            return new String[]{season, saying, explanation, plantAdvice};
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback: built-in MONTH_DATA
        for (String[] row : MONTH_DATA) {
            if (row[0].equals(targetMonth)) {
                return new String[]{row[1], row[2], row[3], row[4]};
            }
        }

        // Last-resort defaults (always Sindhi)
        return new String[]{
            "گرما",
            "ڄيٺ جي جهل، پاڻيءَ جو مل.",
            "ڄيٺ ۾ گرمي وڌندي آهي، تنهنڪري ٻنيءَ جي آلاڻ تي نظر رکڻ گهرجي.",
            "🌱 ڪپهه · ڪکيارو · مونگ"
        };
    }

    // ─────────────────────────────────────────────────────────
    private static String loadAssetString(Context context, String path) {
        try (InputStream is = context.getAssets().open(path);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
