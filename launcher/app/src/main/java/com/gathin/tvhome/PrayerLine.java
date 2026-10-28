package com.gathin.tvhome;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Next prayer and a countdown, from Aladhan (Yanbu, Umm al-Qura). Call off the main thread. */
final class PrayerLine {
    private static final String LAT = "24.0889";
    private static final String LON = "38.0617";
    private static final String METHOD = "4";
    private static final String[] KEYS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String[] NAMES = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final Map<String, long[]> CACHE = new HashMap<>();
    private static long lastFailure;

    private PrayerLine() {}

    static synchronized String text() {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        long[] today = timesFor(cal);
        if (today == null) return null;
        for (int i = 0; i < KEYS.length; i++) {
            if (today[i] > now) return format(NAMES[i], today[i], now);
        }
        cal.add(Calendar.DAY_OF_MONTH, 1);
        long[] tomorrow = timesFor(cal);
        return tomorrow == null ? null : format(NAMES[0], tomorrow[0], now);
    }

    private static String format(String name, long target, long now) {
        long mins = (target - now + 59_999L) / 60_000L;
        String left = mins >= 60 ? (mins / 60) + " س " + (mins % 60) + " د" : mins + " د";
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(target);
        return name + " بعد " + left + "  ·  "
                + String.format(Locale.US, "%02d:%02d", t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE));
    }

    private static long[] timesFor(Calendar day) {
        String key = String.format(Locale.US, "%02d-%02d-%d", day.get(Calendar.DAY_OF_MONTH),
                day.get(Calendar.MONTH) + 1, day.get(Calendar.YEAR));
        long[] cached = CACHE.get(key);
        if (cached != null) return cached;
        if (System.currentTimeMillis() - lastFailure < 60_000L) return null;
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.aladhan.com/v1/timings/" + key
                    + "?latitude=" + LAT + "&longitude=" + LON + "&method=" + METHOD);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) throw new IOException("http");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject t = new JSONObject(sb.toString()).getJSONObject("data").getJSONObject("timings");
            long[] out = new long[KEYS.length];
            for (int i = 0; i < KEYS.length; i++) {
                String v = t.getString(KEYS[i]);
                Calendar x = (Calendar) day.clone();
                x.set(Calendar.HOUR_OF_DAY, Integer.parseInt(v.substring(0, 2)));
                x.set(Calendar.MINUTE, Integer.parseInt(v.substring(3, 5)));
                x.set(Calendar.SECOND, 0);
                x.set(Calendar.MILLISECOND, 0);
                out[i] = x.getTimeInMillis();
            }
            if (CACHE.size() > 6) CACHE.clear();
            CACHE.put(key, out);
            return out;
        } catch (Exception e) {
            lastFailure = System.currentTimeMillis();
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
