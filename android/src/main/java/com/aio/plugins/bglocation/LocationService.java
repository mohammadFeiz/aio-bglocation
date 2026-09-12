package com.aio.plugins.bglocation;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ActivityManager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationService extends Service {

    private LocationManager locationManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private String serverUrl;
    private String token;
    private long lastSentTime = 0;
    private Location lastLocation;
    public static boolean isRunning = false;
    private static final long MIN_INTERVAL = 8000; // 8 seconds
    private static final float MIN_DISTANCE = 20f; // meters
    private static final String PREFS_NAME = "aio_bglocation";
    private static final String PREF_ACTIVE = "active";
    private static final String PREF_HEARTBEAT = "heartbeat";
    private static final long HEARTBEAT_INTERVAL = 15000;
    private static final long HEARTBEAT_TTL = 45000;
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            writeServiceState(true);
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
        }
    };
    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            float distance = 0;
            long now = System.currentTimeMillis();

            if (lastLocation != null) {
                distance = location.distanceTo(lastLocation);
            }

            if (now - lastSentTime < MIN_INTERVAL && distance < MIN_DISTANCE) {
                return;
            }
            lastLocation = location;
            lastSentTime = now;

            double lat = location.getLatitude();
            double lng = location.getLongitude();

            executor.execute(() -> sendToServer(lat, lng));
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            serverUrl = intent.getStringExtra("url");
            token = intent.getStringExtra("token");
        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        isRunning = true;
        startHeartbeat();

        startForeground(1, createNotification());

        try {
            locationManager.removeUpdates(listener);
            requestUpdates(LocationManager.GPS_PROVIDER);
            requestUpdates(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return START_REDELIVER_INTENT;
    }

    public static boolean isActive(Context context) {
        if (isRunning || isServiceRunning(context)) {
            return true;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean active = prefs.getBoolean(PREF_ACTIVE, false);
        long heartbeat = prefs.getLong(PREF_HEARTBEAT, 0);
        return active && System.currentTimeMillis() - heartbeat < HEARTBEAT_TTL;
    }

    private static boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (LocationService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    private void startHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        writeServiceState(true);
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
    }

    private void stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        writeServiceState(false);
    }

    private void writeServiceState(boolean active) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_ACTIVE, active)
                .putLong(PREF_HEARTBEAT, System.currentTimeMillis())
                .apply();
    }

    private void requestUpdates(String provider) throws SecurityException {
        if (locationManager == null || !locationManager.isProviderEnabled(provider)) {
            return;
        }
        locationManager.requestLocationUpdates(
                provider,
                5000,
                0,
                listener);
    }

    private Notification createNotification() {

        String channelId = "tracker";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Tracker",
                    NotificationManager.IMPORTANCE_LOW);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Tracking active")
                .setContentText("Service running")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    private void sendToServer(double lat, double lng) {
        try {
            URL url = new URL(serverUrl);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setDoOutput(true);

            String json = "{"
                    + "\"lat\":" + lat + ","
                    + "\"lng\":" + lng + ","
                    + "\"timestamp\":" + System.currentTimeMillis()
                    + "}";

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes());
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                ExamplePlugin.emitLocation(lat, lng, true, null);
            } else {
                ExamplePlugin.emitLocation(lat, lng, false, "HTTP " + responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            ExamplePlugin.emitLocation(lat, lng, false, e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        stopHeartbeat();
        if (locationManager != null) {
            locationManager.removeUpdates(listener);
        }
        executor.shutdownNow();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
