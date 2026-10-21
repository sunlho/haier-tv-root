package io.github.sunlho.haierroot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

final class AdbRootSupport {
    static final String TAG = "AdbRootSupport";
    private AdbRootSupport() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("adb-root-support", Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context context) {
        return preferences(context).getBoolean("restore_at_boot", false);
    }

    static void setEnabled(Context context, boolean value) {
        if (!preferences(context).edit().putBoolean("restore_at_boot", value).commit()) {
            throw new IllegalStateException("Could not save boot setting");
        }
    }

    static String lastEvent(Context context) {
        return preferences(context).getString("last_event", "尚未启用");
    }

    static void record(Context context, String value) {
        preferences(context).edit().putString("last_event", value).commit();
        Log.i(TAG, value);
    }

    static final class Compatibility {
        final boolean supported;
        final String description;
        Compatibility(boolean supported, String description) {
            this.supported = supported;
            this.description = description;
        }
    }

    static synchronized Compatibility inspect(Context context) throws Exception {
        JSONObject patch = new JSONObject(new String(VendorRoot.read(context.getAssets().open("adbd-root-patch.json")), "UTF-8"));
        File copy = new File(context.getCacheDir(), "adbd-inspect");
        try {
            VendorRoot.run(context, "cp /sbin/adbd '" + copy.getAbsolutePath() + "'\nchmod 644 '" + copy.getAbsolutePath() + "'");
            String hash = sha256(VendorRoot.read(new FileInputStream(copy)));
            boolean supported = hash.equals(patch.getString("original_sha256")) || hash.equals(patch.getString("patched_sha256"));
            return new Compatibility(supported, (supported ? "已匹配，可用" : "未匹配，保留通用 root 命令") + "\nadbd SHA-256: " + hash);
        } finally { copy.delete(); }
    }

    static synchronized String install(Context context, boolean fromBoot) throws Exception {
        JSONObject patch = new JSONObject(new String(VendorRoot.read(context.getAssets().open("adbd-root-patch.json")), "UTF-8"));
        if (!inspect(context).supported) {
            throw new IllegalStateException("This adbd does not match the verified patch; use vendor root commands instead");
        }
        File directory = new File(context.getFilesDir(), "adb-root");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Could not create app directory");
        File original = new File(directory, "adbd-original");
        VendorRoot.run(context,
            "case \"$(cat /proc/mounts)\" in\n*\" /sbin/adbd \"*) /system/bin/busybox umount -l /sbin/adbd;;\nesac\n"
            + "cp /sbin/adbd '" + original.getAbsolutePath() + "'\nchmod 644 '" + original.getAbsolutePath() + "'");
        byte[] data = VendorRoot.read(new FileInputStream(original));
        if (!patch.getString("original_sha256").equals(sha256(data))) {
            throw new IllegalStateException("Unrecognized original adbd; firmware was not modified");
        }
        JSONArray changes = patch.getJSONArray("patches");
        for (int n = 0; n < changes.length(); n++) {
            JSONObject change = changes.getJSONObject(n);
            int offset = change.getInt("offset");
            byte[] before = hex(change.getString("before"));
            byte[] after = hex(change.getString("after"));
            if (before.length != after.length || offset < 0 || offset + before.length > data.length) {
                throw new IllegalStateException("Invalid patch range");
            }
            for (int i = 0; i < before.length; i++) {
                if (data[offset + i] != before[i]) throw new IllegalStateException("Patch instruction mismatch");
            }
            System.arraycopy(after, 0, data, offset, after.length);
        }
        String hash = sha256(data);
        if (!patch.getString("patched_sha256").equals(hash)) throw new IllegalStateException("Patched adbd checksum mismatch");
        File upload = new File(context.getCacheDir(), "adbd-upload");
        VendorRoot.write(upload, data);
        File staged = new File(directory, "adbd-new");
        File installed = new File(directory, "adbd-enabled");
        String result;
        try {
            result = VendorRoot.run(context,
                "cp '" + upload.getAbsolutePath() + "' '" + staged.getAbsolutePath() + "'\n"
                + "chown 0:0 '" + staged.getAbsolutePath() + "'\nchmod 755 '" + staged.getAbsolutePath() + "'\n"
                + "mv '" + staged.getAbsolutePath() + "' '" + installed.getAbsolutePath() + "'\n"
                + "mount -o bind '" + installed.getAbsolutePath() + "' /sbin/adbd\n"
                + "setprop service.adb.root 0\nsetprop ctl.restart adbd\n"
                + "echo ADB_ROOT_SUPPORT_READY\ncat /proc/sys/kernel/random/boot_id");
        } finally { upload.delete(); }
        if (!result.contains("ADB_ROOT_SUPPORT_READY")) throw new IllegalStateException("Support install was not confirmed");
        String event = (fromBoot ? "BOOT_RESTORE_OK" : "MANUAL_INSTALL_OK")
            + " appUid=" + Process.myUid() + " sha256=" + hash + "\n" + result;
        record(context, event);
        return event;
    }

    static synchronized String normalAdb(Context context) throws Exception {
        String result = VendorRoot.run(context, "setprop service.adb.root 0\nsetprop ctl.restart adbd\necho NORMAL_ADB_REQUESTED");
        record(context, "NORMAL_ADB_REQUESTED");
        return result;
    }

    static synchronized String disable(Context context) throws Exception {
        setEnabled(context, false);
        String result = VendorRoot.run(context,
            "case \"$(cat /proc/mounts)\" in\n*\" /sbin/adbd \"*) /system/bin/busybox umount -l /sbin/adbd;;\nesac\n"
            + "setprop service.adb.root 0\nsetprop ctl.restart adbd\necho ORIGINAL_ADBD_RESTORED");
        record(context, "ORIGINAL_ADBD_RESTORED");
        return result;
    }

    private static byte[] hex(String value) {
        if (value.length() % 2 != 0) throw new IllegalArgumentException("Invalid hex string");
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hex digit");
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        String digits = "0123456789abcdef";
        StringBuilder output = new StringBuilder();
        for (byte value : hash) {
            output.append(digits.charAt((value >> 4) & 15));
            output.append(digits.charAt(value & 15));
        }
        return output.toString();
    }
}
