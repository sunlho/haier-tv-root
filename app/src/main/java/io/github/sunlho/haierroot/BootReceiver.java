package io.github.sunlho.haierroot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && AdbRootSupport.isEnabled(context)) {
            context.startService(new Intent(context, AdbRootService.class));
        }
    }
}
