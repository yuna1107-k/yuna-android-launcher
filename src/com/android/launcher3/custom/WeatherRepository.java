package com.android.launcher3.custom;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Current weather for the glance panel, from Open-Meteo (free, no API key).
 *
 * Also keeps a 7-day daily forecast for the week panel.
 * Uses the approximate (coarse) location, rounded to about 1 km before it is sent. Results are
 * cached for 30 minutes.
 */
public final class WeatherRepository {

    private static final String TAG = "WeatherRepository";
    private static final String PREFS = "glance_weather";
    private static final long MAX_AGE_MS = 30 * 60 * 1000;
    private static final long RETRY_AFTER_ERROR_MS = 5 * 60 * 1000;
    private static final int TIMEOUT_MS = 10_000;

    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static long sLastAttempt;
    private static String sLastDaily;

    /** Weather snapshot. */
    public static final class Weather {
        public final int temperature;
        public final int high;
        public final int low;
        public final int code;
        public final boolean isDay;

        Weather(int temperature, int high, int low, int code, boolean isDay) {
            this.temperature = temperature;
            this.high = high;
            this.low = low;
            this.code = code;
            this.isDay = isDay;
        }

        /** Emoji for the WMO weather code. */
        public String symbol() {
            if (code == 0) return isDay ? "☀️" : "🌙";
            if (code <= 2) return isDay ? "🌤️" : "☁️";
            if (code == 3) return "☁️";
            if (code == 45 || code == 48) return "🌫️";
            if (code >= 51 && code <= 57) return "🌦️";
            if (code >= 61 && code <= 67) return "🌧️";
            if (code >= 71 && code <= 77) return "❄️";
            if (code >= 80 && code <= 82) return "🌦️";
            if (code == 85 || code == 86) return "🌨️";
            if (code >= 95) return "⛈️";
            return "🌡️";
        }
    }

    private WeatherRepository() { }

    public static boolean hasLocationPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns the last fetched weather, or null if there is none. */
    @Nullable
    public static Weather getCached(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains("time")) {
            return null;
        }
        return new Weather(p.getInt("temp", 0), p.getInt("high", 0), p.getInt("low", 0),
                p.getInt("code", 0), p.getBoolean("day", true));
    }

    /** Daily forecast keyed by ISO date (yyyy-MM-dd), from the last fetch. */
    public static Map<String, Weather> getDaily(Context context) {
        Map<String, Weather> result = new HashMap<>();
        String raw = prefs(context).getString("daily", null);
        if (raw == null) {
            return result;
        }
        try {
            JSONObject daily = new JSONObject(raw);
            JSONArray dates = daily.getJSONArray("time");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray highs = daily.getJSONArray("temperature_2m_max");
            JSONArray lows = daily.getJSONArray("temperature_2m_min");
            for (int i = 0; i < dates.length(); i++) {
                int high = (int) Math.round(highs.getDouble(i));
                int low = (int) Math.round(lows.getDouble(i));
                result.put(dates.getString(i), new Weather(high, high, low, codes.getInt(i), true));
            }
        } catch (Exception e) {
            Log.w(TAG, "Bad daily forecast cache", e);
        }
        return result;
    }

    /** Fetches new weather if the cache is stale; {@code onUpdated} runs on the main thread. */
    public static void refreshIfStale(Context context, Consumer<Weather> onUpdated) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        if (!hasLocationPermission(app)
                || (now - prefs(app).getLong("time", 0) < MAX_AGE_MS
                        && prefs(app).contains("daily"))
                || now - sLastAttempt < RETRY_AFTER_ERROR_MS) {
            return;
        }
        sLastAttempt = now;
        withLocation(app, location -> NETWORK.execute(() -> {
            Weather weather = fetch(location);
            if (weather != null) {
                prefs(app).edit()
                        .putLong("time", System.currentTimeMillis())
                        .putInt("temp", weather.temperature)
                        .putInt("high", weather.high)
                        .putInt("low", weather.low)
                        .putInt("code", weather.code)
                        .putBoolean("day", weather.isDay)
                        .putString("daily", sLastDaily)
                        .apply();
                MAIN.post(() -> onUpdated.accept(weather));
            }
        }));
    }

    @SuppressWarnings("MissingPermission")
    private static void withLocation(Context context, Consumer<Location> callback) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        if (lm == null) {
            return;
        }
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        if (best != null) {
            callback.accept(best);
            return;
        }
        String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER : LocationManager.FUSED_PROVIDER;
        try {
            lm.getCurrentLocation(provider, null, NETWORK, l -> {
                if (l != null) {
                    callback.accept(l);
                }
            });
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "No location for weather", e);
        }
    }

    @Nullable
    private static Weather fetch(Location location) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=7",
                location.getLatitude(), location.getLongitude());
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Weather request failed: " + conn.getResponseCode());
                return null;
            }
            JSONObject json = new JSONObject(readAll(conn.getInputStream()));
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            sLastDaily = daily.toString();
            return new Weather(
                    (int) Math.round(current.getDouble("temperature_2m")),
                    (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                    (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                    current.getInt("weather_code"),
                    current.optInt("is_day", 1) == 1);
        } catch (Exception e) {
            Log.w(TAG, "Unable to fetch weather", e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
