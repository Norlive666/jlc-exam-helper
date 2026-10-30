package com.ai.assistance.operit.quiz;

import android.app.NotificationManager;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Bundle;
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
import org.robolectric.RuntimeEnvironment;
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
    // notification and Activity behavior, replacing only key encryption/decryption.
    @Implements(value = AgentSettings.class, isInAndroidSdk = false)
    public static class SavedTestKey {
        @Implementation protected String apiKey() { return "test-only-not-a-real-key"; }
        @Implementation protected void saveApiKey(String key) {
            RuntimeEnvironment.getApplication().getSharedPreferences("quiz_agent", Context.MODE_PRIVATE)
                .edit().putString("api_key", key).apply();
        }
    }

    private MainActivity open(boolean enabled, boolean notifications) {
        return open(enabled, notifications, true);
    }

    private MainActivity open(boolean enabled, boolean notifications, boolean launcher) {
        return open(enabled, notifications, launcher, null);
    }

    private MainActivity open(boolean enabled, boolean notifications, boolean launcher, Bundle state) {
        Intent intent = launcher ? launcherIntent() : new Intent();
        controller = Robolectric.buildActivity(MainActivity.class, intent).create(state);
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
        connectService(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(new ComponentName(activity, QuizTestActivity.class), opened.getComponent());
        assertTrue(new AgentSettings(activity).isRunning());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        controller.pause().resume();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(new AgentSettings(activity).isRunning());
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

    @Test public void openingConfigurationWithoutLauncherClickDoesNotStartRecognition() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertFalse(new AgentSettings(activity).isRunning());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void enablingPermissionAndReturningDoesNotStartRecognition() {
        MainActivity activity = open(false, true);
        controller.pause();
        Settings.Secure.putString(activity.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            new ComponentName(activity, QuizAccessibilityService.class).flattenToString());
        connectService(activity);
        controller.resume();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertFalse(new AgentSettings(activity).isRunning());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void restoringConfigurationDoesNotReplayOldLauncherClick() {
        MainActivity activity = open(true, true, true, new Bundle());
        connectService(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertFalse(new AgentSettings(activity).isRunning());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void serviceConnectionDoesNotRestoreStaleRunningFlag() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("quiz_agent", Context.MODE_PRIVATE).edit()
            .putString("api_key", "unused-in-connection-tests").apply();
        AgentSettings settings = new AgentSettings(context);
        settings.setRunning(true);
        connectService(context);
        assertFalse(settings.isRunning());
        assertEquals(0, Shadows.shadowOf(context.getSystemService(NotificationManager.class))
            .getAllNotifications().size());
    }

    @Test public void tappingLauncherIconStartsExistingConfigurationActivity() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        controller.pause();
        controller.newIntent(launcherIntent());
        controller.resume();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(new AgentSettings(activity).isRunning());
        Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(new ComponentName(activity, QuizTestActivity.class), opened.getComponent());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void countdownWaitsInBackgroundThenStartsWithoutOpeningPage() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        assertFalse(new AgentSettings(activity).isRunning());
        assertEquals(1, notifications(activity).getAllNotifications().size());
        controller.pause();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59));
        assertFalse(new AgentSettings(activity).isRunning());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(new AgentSettings(activity).isRunning());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void notificationCloseCancelsCountdownPermanently() throws Exception {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        Notification notification = notifications(activity).getAllNotifications().get(0);
        notification.contentIntent.send();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertFalse(new AgentSettings(activity).isRunning());
        assertEquals(0, notifications(activity).getAllNotifications().size());
    }

    @Test public void replacingCountdownDoesNotRunAtOldDeadline() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        schedule(activity, "2");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertFalse(new AgentSettings(activity).isRunning());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(89));
        assertFalse(new AgentSettings(activity).isRunning());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(new AgentSettings(activity).isRunning());
    }

    @Test public void launcherDuringCountdownDoesNotStartEarlyOrResetDeadline() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        controller.pause();
        controller.newIntent(launcherIntent());
        controller.resume();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertFalse(new AgentSettings(activity).isRunning());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59));
        assertTrue(new AgentSettings(activity).isRunning());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void immediateStartOverridesCountdown() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "2");
        findText(activity.getWindow().getDecorView(), "保存并进入测试页").performClick();
        assertTrue(new AgentSettings(activity).isRunning());
        service.get().stopAgent();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3));
        assertFalse(new AgentSettings(activity).isRunning());
    }

    @Test public void interruptCancelsCountdown() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        service.get().onInterrupt();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertFalse(new AgentSettings(activity).isRunning());
        assertEquals(0, notifications(activity).getAllNotifications().size());
    }

    @Test public void missingNotificationPermissionAtDeadlineDoesNotReplayLater() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        notifications(activity).setNotificationsEnabled(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertFalse(new AgentSettings(activity).isRunning());
        notifications(activity).setNotificationsEnabled(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertFalse(new AgentSettings(activity).isRunning());
    }

    @Test public void invalidMinuteInputDoesNotCreateTask() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        for (String input : new String[]{"", "0", "-1", "abc", "2147483648"}) {
            schedule(activity, input);
            assertFalse(new AgentSettings(activity).isRunning());
            assertEquals(0, notifications(activity).getAllNotifications().size());
        }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertFalse(new AgentSettings(activity).isRunning());
    }

    @Test public void historyRemainsAccessibleAndDoesNotCancelCountdown() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        View history = findText(activity.getWindow().getDecorView(), "识题历史");
        assertNotNull("Missing in-app history entry", history);
        history.performClick();
        Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(new ComponentName(activity, QuizHistoryActivity.class), opened.getComponent());
        controller.pause();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
        assertTrue(new AgentSettings(activity).isRunning());
    }

    @Test public void applicationStopButtonCancelsCountdown() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        schedule(activity, "1");
        View stop = findText(activity.getWindow().getDecorView(), "关闭识题");
        assertNotNull("Missing in-app stop entry", stop);
        stop.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertFalse(new AgentSettings(activity).isRunning());
        assertEquals(0, notifications(activity).getAllNotifications().size());
    }

    @Test public void recognizeNowRemainsAccessibleWithoutOverlay() {
        MainActivity activity = open(true, true, false);
        connectService(activity);
        View recognize = findText(activity.getWindow().getDecorView(), "立即重识别");
        assertNotNull("Missing in-app recognition entry", recognize);
        recognize.performClick();
        assertTrue(new AgentSettings(activity).isRunning());
    }

    private org.robolectric.shadows.ShadowNotificationManager notifications(Context context) {
        return Shadows.shadowOf(context.getSystemService(NotificationManager.class));
    }

    private void schedule(MainActivity activity, String minutes) {
        findKey(activity.getWindow().getDecorView()).setText("test-only-not-a-real-key");
        EditText input = findDelay(activity.getWindow().getDecorView());
        assertNotNull("Missing countdown minute input", input);
        input.setText(minutes);
        View schedule = findText(activity.getWindow().getDecorView(), "定时开始");
        assertNotNull("Missing countdown action", schedule);
        schedule.performClick();
    }

    private EditText findDelay(View view) {
        if (view instanceof EditText && "延时分钟数".contentEquals(((EditText) view).getHint())) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findDelay(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Intent launcherIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    }

    private void connectService(Context context) {
        // Keep capture idle: these tests verify startup, not screenshots or HTTP.
        Shadows.shadowOf(context.getSystemService(PowerManager.class)).setIsInteractive(false);
        service = Robolectric.buildService(QuizAccessibilityService.class).create();
        service.get().onServiceConnected();
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
