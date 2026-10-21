package io.github.sunlho.haierroot;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** Inspect optional vendor support before exposing privileged operations. */
public final class MainActivity extends Activity {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean rootAvailable;
    private boolean adbPatchAvailable;
    private TextView environment;
    private TextView supportState;
    private TextView output;
    private EditText command;
    private Button inspect;
    private Button execute;
    private Button enableBoot;
    private Button normalAdb;
    private Button disableBoot;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 24, 36, 24);
        layout.setBackgroundColor(0xff101216);
        TextView heading = text(layout, getString(R.string.app_name), 28);
        heading.setTextColor(0xffffffff);
        text(layout, getString(R.string.verified_model), 17);
        environment = text(layout, "正在检测环境…", 17);
        supportState = text(layout, "", 17);
        inspect = button(layout, "重新检测环境", new View.OnClickListener() {
            @Override public void onClick(View view) { inspectEnvironment(); }
        });
        text(layout, "厂商 root 命令（检测通过后可用，限 10 秒内完成的短命令）", 17);
        command = new EditText(this);
        command.setText("id");
        command.setTextColor(0xffe0e5ee);
        command.setSingleLine(false);
        command.setMaxLines(3);
        layout.addView(command, new LinearLayout.LayoutParams(-1, -2));
        execute = button(layout, "执行 root 命令", new View.OnClickListener() {
            @Override public void onClick(View view) { operation("command"); }
        });
        text(layout, "ADB root 补丁（仅对已匹配的 adbd 开放）", 17);
        enableBoot = button(layout, "开启开机恢复 adb root 功能", new View.OnClickListener() {
            @Override public void onClick(View view) { operation("enable"); }
        });
        normalAdb = button(layout, "恢复普通 ADB（保留 adb root 功能）", new View.OnClickListener() {
            @Override public void onClick(View view) { operation("normal"); }
        });
        disableBoot = button(layout, "关闭开机恢复并还原原版 adbd", new View.OnClickListener() {
            @Override public void onClick(View view) { operation("disable"); }
        });
        output = text(layout, "", 16);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setMinLines(8);
        scroll.addView(layout);
        setContentView(scroll);
        refreshButtons();
        inspectEnvironment();
    }

    private TextView text(LinearLayout layout, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xffc0c8d4);
        layout.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button button(LinearLayout layout, String label, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(label);
        view.setOnClickListener(listener);
        layout.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void refreshButtons() {
        boolean idle = !running.get();
        inspect.setEnabled(idle);
        execute.setEnabled(idle && rootAvailable);
        command.setEnabled(idle && rootAvailable);
        enableBoot.setEnabled(idle && rootAvailable && adbPatchAvailable);
        boolean managed = AdbRootSupport.isEnabled(this) || adbPatchAvailable;
        normalAdb.setEnabled(idle && rootAvailable && managed);
        disableBoot.setEnabled(idle && (rootAvailable && managed || AdbRootSupport.isEnabled(this)));
        supportState.setText("开机恢复：" + (AdbRootSupport.isEnabled(this) ? "已开启" : "未开启")
            + "  |  应用 UID：" + Process.myUid());
    }

    private void inspectEnvironment() {
        if (!running.compareAndSet(false, true)) return;
        rootAvailable = false;
        adbPatchAvailable = false;
        refreshButtons();
        environment.setText("正在检测环境…");
        output.setText("");
        new Thread(new Runnable() {
            @Override public void run() {
                final StringBuilder report = new StringBuilder();
                report.append("品牌：").append(Build.MANUFACTURER).append("\n型号：").append(Build.MODEL)
                    .append("\n系统：").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT)
                    .append("\n已验证：海尔 M638，其他型号以本机检测结果为准。\n");
                boolean root = false;
                boolean patch = false;
                try {
                    VendorInterface.connect();
                    report.append("厂商接口：存在\n");
                    String proof = VendorRoot.run(MainActivity.this, "/system/bin/id");
                    report.append("执行身份：").append(proof).append('\n');
                    root = VendorInterface.isRootOutput(proof);
                    report.append("root 命令：").append(root ? "可用" : "不可用（服务未以 root 执行）").append('\n');
                    if (root) {
                        try {
                            AdbRootSupport.Compatibility result = AdbRootSupport.inspect(MainActivity.this);
                            patch = result.supported;
                            report.append("ADB root 补丁：").append(result.description).append('\n');
                        } catch (Throwable error) {
                            report.append("ADB root 补丁：无法检查，通用 root 命令仍可用\n").append(error).append('\n');
                        }
                    }
                } catch (ClassNotFoundException error) {
                    report.append("厂商接口：不存在，此电视暂不支持本应用的 root 方式。\n");
                } catch (Throwable error) {
                    report.append("厂商 root 接口不可用：").append(error).append('\n');
                }
                final boolean canRoot = root;
                final boolean canPatch = patch;
                final String result = report.toString();
                Log.i("HaierRoot", result);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        rootAvailable = canRoot;
                        adbPatchAvailable = canPatch;
                        environment.setText(result);
                        running.set(false);
                        refreshButtons();
                        inspect.requestFocus();
                    }
                });
            }
        }, "inspect-vendor-root").start();
    }

    private void operation(final String action) {
        if (!rootAvailable && !"disable".equals(action)) return;
        if ("enable".equals(action) && !adbPatchAvailable) return;
        final String shell = command.getText().toString().trim();
        if ("command".equals(action) && shell.length() == 0) {
            output.setText("请输入命令。");
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        refreshButtons();
        output.setText("执行中…");
        new Thread(new Runnable() {
            @Override public void run() {
                String result;
                try {
                    if ("command".equals(action)) {
                        result = VendorRoot.run(MainActivity.this, shell);
                        if (result.length() == 0) result = "命令执行成功，无输出。";
                    } else if ("enable".equals(action)) {
                        result = AdbRootSupport.install(MainActivity.this, false);
                        AdbRootSupport.setEnabled(MainActivity.this, true);
                        result = "已开启开机恢复。ADB 重连后可执行 adb root。\n" + result;
                    } else if ("normal".equals(action)) {
                        result = AdbRootSupport.normalAdb(MainActivity.this);
                    } else {
                        result = AdbRootSupport.disable(MainActivity.this);
                    }
                } catch (Throwable error) {
                    result = "操作失败：\n" + Log.getStackTraceString(error);
                }
                final String display = result;
                Log.i("HaierRoot", result);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        output.setText(display);
                        running.set(false);
                        refreshButtons();
                    }
                });
            }
        }, "vendor-root-operation").start();
    }
}
