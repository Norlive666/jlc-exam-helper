package com.ai.assistance.operit.quiz;

import com.ai.assistance.operit.provider.UIAccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.BitmapFactory;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class QuizAccessibilityService extends UIAccessibilityService {
    static final int NOTIFICATION_ID = 6101;
    private static final String CHANNEL = "quiz_agent_status";
    private static volatile QuizAccessibilityService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AgentSettings settings;
    private DeepSeekClient client;
    private Future<?> task;
    private volatile boolean running;

    static QuizAccessibilityService current() { return instance; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        settings = new AgentSettings(this);
        instance = this;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
            getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        if (settings.isRunning()) {
            try { startAgent(); }
            catch (Exception error) { showError(error); }
        }
    }

    void startAgent() throws Exception {
        if (!notificationAvailable()) {
            throw new IllegalStateException(getString(R.string.permission_needed));
        }
        if (running) return;
        DeepSeekClient model = new DeepSeekClient(settings.apiKey());
        client = model;
        long token = generation.incrementAndGet();
        settings.setRunning(true);
        running = true;
        showNotification(getString(R.string.waiting));
        task = worker.submit(() -> {
            QuizSession session = new QuizSession(new PhoneScreen(token), model);
            try {
                while (active(token)) {
                    if (!notificationAvailable()) {
                        main.post(() -> { if (active(token)) stopAgent(); });
                        return;
                    }
                    session.scan(() -> active(token) && notificationAvailable());
                    Thread.sleep(3000);
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                if (active(token)) main.post(() -> {
                    if (active(token)) {
                        running = false;
                        generation.incrementAndGet();
                        settings.setRunning(false);
                        showError(error);
                    }
                });
            } finally {
                model.close();
            }
        });
    }

    void stopAgent() {
        running = false;
        generation.incrementAndGet();
        if (settings != null) settings.setRunning(false);
        if (task != null) task.cancel(true);
        if (client != null) client.close();
        getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }

    private boolean active(long token) {
        return running && generation.get() == token;
    }

    private boolean notificationAvailable() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && channel != null
            && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        super.onAccessibilityEvent(event);
    }

    @Override public void onInterrupt() { stopAgent(); super.onInterrupt(); }

    @Override public void onDestroy() {
        stopAgent();
        worker.shutdownNow();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void showNotification(String text) {
        Intent stop = new Intent(this, StopReceiver.class);
        PendingIntent action = PendingIntent.getBroadcast(this, 0, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_agent)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setShowWhen(false)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(action)
            .setDeleteIntent(action)
            .addAction(new Notification.Action.Builder(null, getString(R.string.stop), action).build())
            .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    private void showError(Exception error) {
        Log.e("OperitQuiz", "Agent stopped: " + error.getClass().getSimpleName());
        String detail = error.getMessage();
        showNotification(getString(R.string.error_prefix,
            detail == null ? error.getClass().getSimpleName() : detail));
    }

    private void status(long token, int text) {
        main.post(() -> { if (active(token)) showNotification(getString(text)); });
    }

    private WindowState windowState() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            CharSequence packageName = root.getPackageName();
            if (packageName == null || packageName.toString().equals(getPackageName())) return null;
            Rect bounds = new Rect();
            root.getBoundsInScreen(bounds);
            String xml = captureUiHierarchyAsXml();
            if (xml.isEmpty()) return null;
            String text = ScreenFrame.normalizeUiText(xml);
            StringBuilder selection = new StringBuilder();
            readSelection(root, selection, new int[]{0});
            int rotation = getSystemService(WindowManager.class).getDefaultDisplay().getRotation();
            return new WindowState(packageName.toString(), root.getWindowId(), rotation,
                bounds, text, selection.toString());
        } finally {
            root.recycle();
        }
    }

    private void readSelection(AccessibilityNodeInfo node, StringBuilder selection, int[] count) {
        if (++count[0] > 1200 || !node.isVisibleToUser()) return;
        if (node.isCheckable() || node.isClickable() || node.isSelected()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            selection.append(bounds.toShortString()).append(':')
                .append(node.isChecked()).append(':').append(node.isSelected()).append('\n');
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { readSelection(child, selection, count); }
            finally { child.recycle(); }
        }
    }

    private final class PhoneScreen implements QuizSession.Screen {
        private final long token;
        private ScreenFrame lastCaptured;

        PhoneScreen(long token) { this.token = token; }

        @Override public ScreenFrame capture() throws Exception {
            CompletableFuture<WindowState> snapshot = new CompletableFuture<>();
            main.post(() -> {
                try {
                    if (!active(token) || !notificationAvailable()
                            || !getSystemService(PowerManager.class).isInteractive()
                            || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                        snapshot.complete(null);
                    } else {
                        snapshot.complete(windowState());
                    }
                } catch (RuntimeException error) {
                    snapshot.completeExceptionally(error);
                }
            });
            WindowState before = snapshot.get(5, TimeUnit.SECONDS);
            if (before == null || !active(token)) return null;
            File file = File.createTempFile("operit-screen-", ".png", getCacheDir());
            Bitmap bitmap = null;
            try {
                // Operit's provider owns screenshot rate-limiting and HardwareBuffer lifetime.
                if (!captureScreenshot(file.getAbsolutePath(), "png") || !active(token)) return null;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) throw new IllegalStateException("截图无法解码");
                ScreenFrame frame = encodeFrame(bitmap, before);
                CompletableFuture<Boolean> unchanged = new CompletableFuture<>();
                main.post(() -> {
                    try {
                        unchanged.complete(active(token) && notificationAvailable()
                            && before.matches(windowState()));
                    } catch (RuntimeException error) {
                        unchanged.completeExceptionally(error);
                    }
                });
                if (!unchanged.get(5, TimeUnit.SECONDS)) return null;
                lastCaptured = frame;
                return frame;
            } finally {
                if (bitmap != null) bitmap.recycle();
                file.delete();
            }
        }

        @Override public boolean tap(QuizProtocol.Tap tap) throws Exception {
            ScreenFrame expected = lastCaptured;
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            main.post(() -> {
                try {
                    WindowState current = windowState();
                    if (!active(token) || result.isDone() || expected == null || current == null
                            || !notificationAvailable()
                            || !current.app.equals(expected.app) || current.id != expected.windowId
                            || current.rotation != expected.rotation || !current.text.equals(expected.uiText)
                            || !current.selection.equals(expected.selectionState)) {
                        result.complete(false);
                        return;
                    }
                    performClick(tap.x, tap.y, completed -> {
                        result.complete(completed);
                        return kotlin.Unit.INSTANCE;
                    });
                } catch (RuntimeException error) {
                    result.completeExceptionally(error);
                }
            });
            boolean clicked;
            try {
                clicked = result.get(3, TimeUnit.SECONDS);
            } catch (Exception error) {
                result.cancel(false);
                throw error;
            }
            if (clicked) Thread.sleep(350);
            return clicked;
        }

        @Override public void status(QuizSession.Status state) {
            int resource;
            switch (state) {
                case ANALYZING: resource = R.string.analyzing; break;
                case SELECTED: resource = R.string.selected; break;
                case SCREEN_CHANGED: resource = R.string.screen_changed; break;
                default: resource = R.string.waiting;
            }
            QuizAccessibilityService.this.status(token, resource);
        }
    }

    private ScreenFrame encodeFrame(Bitmap bitmap, WindowState window) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        byte[] jpeg = OperitScreenshot.encode(bitmap);
        Rect area = new Rect(window.bounds);
        if (!area.intersect(0, 0, width, height)) {
            throw new IllegalStateException("当前窗口没有可读取区域");
        }
        Bitmap region = Bitmap.createBitmap(bitmap, area.left, area.top, area.width(), area.height());
        Bitmap sample = Bitmap.createScaledBitmap(region, 17, 16, true);
        long[] hash = new long[4];
        try {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int bit = y * 16 + x;
                    if (luminance(sample.getPixel(x, y)) > luminance(sample.getPixel(x + 1, y))) {
                        hash[bit / 64] |= 1L << (bit % 64);
                    }
                }
            }
        } finally {
            if (sample != region) sample.recycle();
            if (region != bitmap) region.recycle();
        }
        Bitmap selectionSample = Bitmap.createScaledBitmap(bitmap, 64, 128, true);
        int[] pixels = new int[64 * 128];
        try {
            selectionSample.getPixels(pixels, 0, 64, 0, 0, 64, 128);
        } finally {
            if (selectionSample != bitmap) selectionSample.recycle();
        }
        return new ScreenFrame(jpeg, width, height, window.app,
            window.id, window.rotation, window.text, window.selection, hash, pixels);
    }

    private int luminance(int color) {
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114;
    }

    private static final class WindowState {
        final String app;
        final int id;
        final int rotation;
        final Rect bounds;
        final String text;
        final String selection;

        WindowState(String app, int id, int rotation, Rect bounds, String text, String selection) {
            this.app = app;
            this.id = id;
            this.rotation = rotation;
            this.bounds = bounds;
            this.text = text;
            this.selection = selection;
        }

        boolean matches(WindowState other) {
            return other != null && app.equals(other.app) && id == other.id
                && rotation == other.rotation && bounds.equals(other.bounds) && text.equals(other.text)
                && selection.equals(other.selection);
        }
    }
}
