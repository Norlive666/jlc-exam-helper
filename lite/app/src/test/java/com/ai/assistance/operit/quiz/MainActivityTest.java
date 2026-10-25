package com.ai.assistance.operit.quiz;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import java.time.Duration;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = MainActivityTest.SavedTestKey.class)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private ServiceController<QuizAccessibilityService> service;

    // Android Keystore is unavailable on the JVM. Keep the real service startup,
    // notification, overlay and Activity behavior, replacing only key decryption.
    @Implements(value = AgentSettings.class, isInAndroidSdk = false)
    public static class SavedTestKey {
        @Implementation protected String apiKey() { return "test-only-not-a-real-key"; }
    }

    private MainActivity open(boolean enabled, boolean notifications) {
        controller = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = controller.get();
        // The connection checks do not need to decrypt or send an API key.
        activity.getSharedPreferences("quiz_agent", Context.MODE_PRIVATE).edit()
            .putString("api_key", "unused-in-connection-tests").apply();
        String own = new ComponentName(activity, QuizAccessibilityService.class).flattenToShortString();
        Settings.Secure.putString(activity.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "other.app/.Service" + (enabled ? ":" + own : ""));
        Shadows.shadowOf(activity.getSystemService(NotificationManager.class)).setNotificationsEnabled(notifications);
        controller.start().resume().visible();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        return activity;
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
        if (service != null) service.destroy();
    }

    @Test public void enabledButDisconnectedServiceWaitsInsteadOfAskingForPermission() {
        MainActivity activity = open(true, true);
        assertTrue(hasText(activity.getWindow().getDecorView(), "无障碍已开启，正在等待系统连接服务…"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void anotherEnabledServiceDoesNotGrantOurServicePermission() {
        MainActivity activity = open(false, true);
        assertTrue(hasText(activity.getWindow().getDecorView(), "请先开启本应用的无障碍服务。"));
    }

    @Test public void notificationPermissionIsReportedSeparately() {
        MainActivity activity = open(true, false);
        assertTrue(hasText(activity.getWindow().getDecorView(), "请先允许本应用发送通知。"));
    }

    @Test public void waitingScreenRechecksSystemStateWithoutReopeningSettings() {
        MainActivity activity = open(true, true);
        Settings.Secure.putString(activity.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(hasText(activity.getWindow().getDecorView(), "请先开启本应用的无障碍服务。"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void leavingConfigurationStopsConnectionRetries() {
        MainActivity activity = open(true, true);
        controller.pause();
        Settings.Secure.putString(activity.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(hasText(activity.getWindow().getDecorView(), "无障碍已开启，正在等待系统连接服务…"));
        controller.resume();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(hasText(activity.getWindow().getDecorView(), "请先开启本应用的无障碍服务。"));
    }

    @Test public void lateConnectionEntersTestPageOnlyOnce() {
        MainActivity activity = open(true, true);
        // Keep capture idle: this test verifies startup, not screenshots or HTTP.
        Shadows.shadowOf(activity.getSystemService(PowerManager.class)).setIsInteractive(false);
        service = Robolectric.buildService(QuizAccessibilityService.class).create();
        service.get().onServiceConnected();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(new ComponentName(activity, QuizTestActivity.class), opened.getComponent());
        assertTrue(new AgentSettings(activity).isRunning());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void editingKeyStopsConnectionRetries() {
        MainActivity activity = open(true, true);
        assertTrue(findKey(activity.getWindow().getDecorView()).requestFocus());
        Settings.Secure.putString(activity.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(hasText(activity.getWindow().getDecorView(), "无障碍已开启，正在等待系统连接服务…"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    private EditText findKey(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findKey(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }
}
