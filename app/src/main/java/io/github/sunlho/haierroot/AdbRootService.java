package io.github.sunlho.haierroot;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdbRootService extends Service {
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override public int onStartCommand(Intent intent, int flags, final int startId) {
        if (!AdbRootSupport.isEnabled(this)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!started.compareAndSet(false, true)) return START_NOT_STICKY;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    AdbRootSupport.install(AdbRootService.this, true);
                } catch (Throwable error) {
                    AdbRootSupport.record(AdbRootService.this, "BOOT_RESTORE_FAILED " + Log.getStackTraceString(error));
                } finally {
                    stopSelf(startId);
                }
            }
        }, "restore-adb-root-support").start();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
