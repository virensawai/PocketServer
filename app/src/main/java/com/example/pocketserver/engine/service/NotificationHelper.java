package com.example.pocketserver.engine.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.example.pocketserver.MainActivity;
import com.example.pocketserver.R;
import com.example.pocketserver.engine.model.Deployment;
import com.example.pocketserver.engine.model.DeploymentState;
import com.example.pocketserver.engine.model.DeploymentTelemetry;

/**
 * Handles creation of notification channels and constructs production-grade
 * foreground service notifications and data alert notifications.
 */
public class NotificationHelper {

    public static final String CHANNEL_ID_SERVICE = "pocketserver_service_channel";
    public static final String CHANNEL_ID_ALERTS = "pocketserver_alerts_channel";

    public static final int NOTIFICATION_ID_FOREGROUND = 1001;
    public static final int NOTIFICATION_ID_DATA_ALERT = 1002;

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    /**
     * Initializes notification channels required on Android 8.0 (API 26) and above.
     */
    public void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            // Channel 1: Ongoing background service (Low importance to avoid constant alert chimes on stat updates)
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE,
                    context.getString(R.string.notification_channel_service_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(context.getString(R.string.notification_channel_service_desc));
            serviceChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(serviceChannel);

            // Channel 2: Alerts (High importance for data limit warnings and errors)
            NotificationChannel alertsChannel = new NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    context.getString(R.string.notification_channel_alerts_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertsChannel.setDescription(context.getString(R.string.notification_channel_alerts_desc));
            alertsChannel.enableVibration(true);
            notificationManager.createNotificationChannel(alertsChannel);
        }
    }

    /**
     * Builds the persistent foreground notification for the active deployment.
     */
    @NonNull
    public Notification buildServiceNotification(
            @Nullable Deployment deployment,
            @Nullable DeploymentTelemetry telemetry) {

        String title = "PocketServer";
        String contentText = "Server active";
        String subText = null;
        String actionUrl = null;

        if (deployment != null) {
            DeploymentState state = deployment.getState();
            String projectName = deployment.getConfig().getProjectName();
            String displayUrl = deployment.getDisplayUrl();
            actionUrl = displayUrl;

            switch (state) {
                case LIVE:
                    contentText = "🟢 " + projectName + (displayUrl != null ? " (" + displayUrl + ")" : " is live");
                    break;
                case CONNECTING:
                    contentText = "🟡 Connecting " + projectName + " to relay...";
                    break;
                case RECONNECTING:
                    contentText = "🟠 Reconnecting " + projectName + "...";
                    break;
                case NETWORK_LOST:
                    contentText = "⚠️ Connection lost: waiting for network";
                    break;
                case FAILED:
                    contentText = "❌ Server error: " + (deployment.getErrorMessage() != null ?
                            deployment.getErrorMessage() : "Unknown failure");
                    break;
                case STARTING:
                case STOPPED:
                default:
                    contentText = "Initializing server...";
                    break;
            }
        }

        if (telemetry != null) {
            subText = telemetry.getRequestCount() + " req · " + telemetry.getFormattedBytes();
        }

        // Main Tap Intent -> Opens MainActivity
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
                .setSmallIcon(R.drawable.ic_server_notification)
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        if (subText != null) {
            builder.setSubText(subText);
        }

        // Action: Stop Server
        Intent stopIntent = new Intent(context, ServerForegroundService.class);
        stopIntent.setAction(ServerForegroundService.ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                context,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.addAction(R.drawable.ic_action_stop, context.getString(R.string.action_stop), stopPendingIntent);

        // Action: Open in Browser (if live and has valid URL)
        if (actionUrl != null && (actionUrl.startsWith("http://") || actionUrl.startsWith("https://"))) {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl));
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent viewPendingIntent = PendingIntent.getActivity(
                    context,
                    2,
                    viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(R.drawable.ic_action_open, context.getString(R.string.action_open_browser), viewPendingIntent);
        }

        return builder.build();
    }

    /**
     * Posts a high-priority alert when data transfer approaches the warning threshold.
     */
    public void showDataWarningAlert(long bytesUsed, long warningLimit) {
        String formatted = DeploymentTelemetry.formatBytes(bytesUsed);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(R.drawable.ic_server_notification)
                .setContentTitle(context.getString(R.string.data_warning_title))
                .setContentText(context.getString(R.string.data_warning_message, formatted))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        safeNotify(NOTIFICATION_ID_DATA_ALERT, notification);
    }

    /**
     * Posts a high-priority alert when data transfer exceeds the hard cap.
     */
    public void showDataLimitExceededAlert(long bytesUsed, long hardCap) {
        String formatted = DeploymentTelemetry.formatBytes(hardCap);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
                .setSmallIcon(R.drawable.ic_server_notification)
                .setContentTitle(context.getString(R.string.data_limit_title))
                .setContentText(context.getString(R.string.data_limit_message, formatted))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        safeNotify(NOTIFICATION_ID_DATA_ALERT, notification);
    }

    private void safeNotify(int id, @NonNull Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification);
        } catch (SecurityException ignored) {
            // Android 13+ POST_NOTIFICATIONS runtime permission not yet granted
        }
    }
}
