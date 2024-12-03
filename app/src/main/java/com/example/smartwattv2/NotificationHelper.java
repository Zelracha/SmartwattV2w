package com.example.smartwattv2;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Locale;

public class NotificationHelper {
    private static final String CHANNEL_ID = "power_monitor_channel";
    private static final String CHANNEL_NAME = "Power Monitor Alerts";
    private static final String CHANNEL_DESC = "Notifications for power consumption alerts";
    private static final int NOTIFICATION_ID = 1001;

    private Context context;
    private NotificationManager notificationManager;
    private Vibrator vibrator;

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void showConsumptionAlert(float currentConsumption, float limit) {
        // Create intent for notification tap action
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("⚠️ Power Consumption Alert!")
                .setContentText(String.format(Locale.US,
                        "Consumption: %.2f kWh has exceeded limit: %.2f kWh",
                        currentConsumption, limit))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(String.format(Locale.US,
                                "Your power consumption (%.2f kWh) has exceeded the set limit (%.2f kWh). " +
                                        "Please check your power usage.",
                                currentConsumption, limit)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setLights(Color.RED, 500, 500)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // Vibrate the device
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
            }
        }

        // Show the notification
        try {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                notificationManagerCompat.notify(NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}