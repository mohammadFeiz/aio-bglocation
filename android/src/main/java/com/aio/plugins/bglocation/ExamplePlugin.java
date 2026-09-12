package com.aio.plugins.bglocation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import android.os.Build;

@CapacitorPlugin(name = "Example", permissions = {
        @Permission(alias = "location", strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }),
        @Permission(alias = "backgroundLocation", strings = {
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }),
        @Permission(alias = "notifications", strings = {
                Manifest.permission.POST_NOTIFICATIONS
        })
})
public class ExamplePlugin extends Plugin {
    private Intent serviceIntent;
    private static ExamplePlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    public static void emitLocation(double lat, double lng, boolean sendSuccess, String sendError) {
        if (instance == null) {
            return;
        }
        JSObject data = new JSObject();
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("sendSuccess", sendSuccess);
        data.put("sendError", sendError);
        instance.notifyListeners("tracker", data, true);
    }

    @PermissionCallback
    private void locationPermCallback(PluginCall call) {
        if (hasPermission("location") && needsBackgroundLocationPermission()) {
            requestPermissionForAlias("backgroundLocation", call, "backgroundLocationPermCallback");
            return;
        }

        resolveLocationPermission(call);
    }

    @PermissionCallback
    private void backgroundLocationPermCallback(PluginCall call) {
        resolveLocationPermission(call);
    }

    @PluginMethod
    public void getLocationPermission(PluginCall call) {

        if (!hasPermission("location")) {
            requestPermissionForAlias("location", call, "locationPermCallback");
            return;
        }

        if (needsBackgroundLocationPermission()) {
            requestPermissionForAlias("backgroundLocation", call, "backgroundLocationPermCallback");
            return;
        }

        resolveLocationPermission(call);
    }

    private boolean needsBackgroundLocationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasPermission("backgroundLocation");
    }

    private void resolveLocationPermission(PluginCall call) {
        boolean granted = hasPermission("location")
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasPermission("backgroundLocation"));

        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    @PluginMethod
    public void getNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        if (getPermissionState("notifications").toString().equals("granted")) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias("notifications", call, "notificationPermissionCallback");
    }

    @PermissionCallback
    private void notificationPermissionCallback(PluginCall call) {

        boolean granted = getPermissionState("notifications")
                .toString()
                .equals("granted");

        JSObject ret = new JSObject();
        ret.put("granted", granted);

        call.resolve(ret);
    }

    @PluginMethod
    public void startTracker(PluginCall call) {

        String url = call.getString("url");
        String token = call.getString("token");

        Context context = getContext();

        try {
            serviceIntent = new Intent(context, LocationService.class);
            serviceIntent.putExtra("url", url);
            serviceIntent.putExtra("token", token);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            call.resolve();

        } catch (Exception e) {
            // هیچ error به JS برنگردان — فقط fail silent
            call.resolve();
        }
    }

    @PluginMethod
    public void stopTracker(PluginCall call) {

        try {
            Intent intent = new Intent(getContext(), LocationService.class);
            getContext().stopService(intent);
        } catch (Exception ignored) {
        }

        call.resolve();
    }

    @PluginMethod
    public void isServiceActive(PluginCall call) {

        JSObject ret = new JSObject();
        ret.put("active", LocationService.isActive(getContext()));

        call.resolve(ret);
    }
}
