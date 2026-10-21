package io.github.sunlho.haierroot;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Short-path calls only; no system UID, su, Java subprocess, or JDWP. */
final class VendorRoot {
    private VendorRoot() {}

    static synchronized String run(Context context, String body) throws Exception {
        String name = "rc" + Long.toHexString(System.nanoTime());
        File script = new File(context.getCacheDir(), name + ".sh");
        File output = new File(context.getCacheDir(), name + ".out");
        File status = new File(context.getCacheDir(), name + ".status");
        String text = "#!/system/bin/sh\nunset LD_PRELOAD\n"
            + "export PATH=/system/bin:/system/xbin:/sbin:/bin:/usr/bin\numask 022\n"
            + "exec > '" + output.getAbsolutePath() + "' 2>&1\n"
            + "trap 'result=$?; echo $result > " + status.getAbsolutePath()
            + "; chmod 644 " + output.getAbsolutePath() + " " + status.getAbsolutePath() + "' 0\n"
            + "set -e\n" + body + "\n";
        write(script, text.getBytes("UTF-8"));
        String command = "CMD=21,/system/bin/sh " + script.getAbsolutePath();
        if (command.getBytes("UTF-8").length > 120) throw new IllegalStateException("Command too long");
        try {
            VendorInterface.connect().execute(command);
            String result = "";
            for (int i = 0; i < 100; i++) {
                if (status.isFile()) result = readText(status).trim();
                if (result.matches("[0-9]+")) break;
                Thread.sleep(100);
            }
            String details = output.isFile() ? readText(output) : "";
            if (!"0".equals(result)) throw new IllegalStateException("Root task status " + result + ": " + details);
            return details.trim();
        } finally {
            script.delete();
            output.delete();
            status.delete();
        }
    }

    static byte[] read(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally { input.close(); }
    }

    static String readText(File file) throws Exception {
        return new String(read(new FileInputStream(file)), "UTF-8");
    }

    static void write(File file, byte[] data) throws Exception {
        FileOutputStream stream = new FileOutputStream(file);
        try { stream.write(data); } finally { stream.close(); }
    }
}
