package de.steve.spiderfoot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import com.chaquo.python.PyException;
import com.chaquo.python.Python;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SpiderFootService extends Service {
    public static final String ACTION_START = "de.steve.spiderfoot.action.START";
    public static final String ACTION_STOP = "de.steve.spiderfoot.action.STOP";
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 1488;

    private static final String CHANNEL_ID = "spiderfoot_scans";
    private static final int NOTIFICATION_ID = 1488;
    private static final String PRIVATE_PREFS = "private_web_endpoint";
    private static final String PRIVATE_ROOT_KEY = "root";

    private static final String STATE_STOPPED = "stopped";
    private static final String STATE_STARTING = "starting";
    private static final String STATE_RUNNING = "running";
    private static final String STATE_STOPPING = "stopping";
    private static final String STATE_ERROR = "error";

    private static volatile String state = STATE_STOPPED;
    private static volatile String lastError = "";

    private final AtomicBoolean serverSubmitted = new AtomicBoolean(false);
    private final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wakeLock;

    public static String getState() {
        return state;
    }

    public static String getLastError() {
        return lastError;
    }

    public static synchronized String getWebRoot(Context context) {
        String root = context.getSharedPreferences(PRIVATE_PREFS, Context.MODE_PRIVATE)
                .getString(PRIVATE_ROOT_KEY, null);
        if (root == null || !root.matches("/android-[a-f0-9]{32}")) {
            root = "/android-" + UUID.randomUUID().toString().replace("-", "");
            context.getSharedPreferences(PRIVATE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PRIVATE_ROOT_KEY, root)
                    .apply();
        }
        return root;
    }

    public static String getBaseUrl(Context context) {
        return "http://" + HOST + ":" + PORT + getWebRoot(context) + "/";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        enterForeground(false);
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            state = STATE_STOPPING;
            stopSelf();
            return START_NOT_STICKY;
        }

        startServerOnce();
        return START_STICKY;
    }

    private void startServerOnce() {
        if (!serverSubmitted.compareAndSet(false, true)) {
            return;
        }

        acquireWakeLock();
        state = STATE_STARTING;
        lastError = "";
        enterForeground(false);

        Thread readinessWatcher = new Thread(this::watchForReadiness, "spiderfoot-ready");
        readinessWatcher.setDaemon(true);
        readinessWatcher.start();

        serverExecutor.execute(() -> {
            try {
                Python.getInstance()
                        .getModule("android_entrypoint")
                        .callAttr(
                                "start_server",
                                getFilesDir().getAbsolutePath(),
                                HOST,
                                PORT,
                                getWebRoot(this)
                        );

                if (!STATE_STOPPING.equals(state)) {
                    state = STATE_STOPPED;
                    stopSelf();
                }
            } catch (PyException exc) {
                lastError = exc.getMessage() == null ? exc.toString() : exc.getMessage();
                state = STATE_ERROR;
                enterForeground(false);
            } catch (Throwable exc) {
                lastError = exc.getClass().getSimpleName() + ": " + String.valueOf(exc.getMessage());
                state = STATE_ERROR;
                enterForeground(false);
            } finally {
                serverSubmitted.set(false);
                if (STATE_STOPPED.equals(state) || STATE_ERROR.equals(state)) {
                    releaseWakeLock();
                }
            }
        });
    }

    private void watchForReadiness() {
        for (int attempt = 0; attempt < 180 && STATE_STARTING.equals(state); attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, PORT), 500);
                state = STATE_RUNNING;
                enterForeground(true);
                return;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException exc) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Hält den lokalen SpiderFoot-Server und laufende Scans aktiv.");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(boolean running) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, SpiderFootService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String text;
        if (STATE_ERROR.equals(state)) {
            text = "SpiderFoot konnte nicht gestartet werden";
        } else {
            text = getString(running
                    ? R.string.notification_running
                    : R.string.notification_starting);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_stat_spiderfoot)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(openPendingIntent)
                .setOngoing(!STATE_ERROR.equals(state))
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_stat_spiderfoot,
                        getString(R.string.notification_stop),
                        stopPendingIntent
                ).build())
                .build();
    }

    private void enterForeground(boolean running) {
        Notification notification = buildNotification(running);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SpiderFootAndroid::Scanner"
        );
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void requestPythonStop() {
        Thread stopThread = new Thread(() -> {
            try {
                if (Python.isStarted()) {
                    Python.getInstance().getModule("android_entrypoint").callAttr("stop_server");
                }
            } catch (Throwable ignored) {
                // Process termination remains the final cleanup path.
            } finally {
                state = STATE_STOPPED;
            }
        }, "spiderfoot-stop");
        stopThread.setDaemon(true);
        stopThread.start();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        stopSelf(startId);
    }

    @Override
    public void onDestroy() {
        state = STATE_STOPPING;
        requestPythonStop();
        releaseWakeLock();
        serverExecutor.shutdownNow();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
