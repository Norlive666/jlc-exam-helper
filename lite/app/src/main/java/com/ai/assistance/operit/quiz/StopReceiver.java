package com.ai.assistance.operit.quiz;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StopReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        new AgentSettings(context).setRunning(false);
        QuizAccessibilityService service = QuizAccessibilityService.current();
        if (service != null) service.stopAgent();
        context.getSystemService(NotificationManager.class)
            .cancel(QuizAccessibilityService.NOTIFICATION_ID);
    }
}
