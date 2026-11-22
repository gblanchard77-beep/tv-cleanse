package com.tvcleanse.installer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

import dadb.AdbKeyPair;
import dadb.Dadb;

/**
 * Puts the TV Cleanse home screen on a TV from your phone. Same steps as
 * tvcleanse.sh home-install and TV-Cleanse-Windows.bat:
 * connect over Wi-Fi (adb, port 5555), download + verify the app, install it,
 * make it Home, and on Google TV turn on the Home button helper.
 */
public class MainActivity extends Activity {

    static final String REL = "https://github.com/gblanchard77-beep/tv-cleanse/releases/latest/download";
    static final String PKG = "com.tvcleanse.home";
    static final String HELPER = PKG + "/.HomeRedirect";

    private EditText ipBox;
    private TextView logView;
    private ScrollView scroll;
    private Button installBtn, removeBtn;
    private volatile boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("TV Cleanse Installer");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("1. On the TV: Settings > Network, find the IP address. Turn on Developer options, then USB debugging and Network debugging.\n"
                + "2. Phone and TV must be on the same Wi-Fi.\n"
                + "3. Type the IP below and tap Install. When the TV asks \"Allow debugging?\", tick Always allow and press OK.");
        help.setTextColor(Color.DKGRAY);
        help.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(help);

        ipBox = new EditText(this);
        ipBox.setHint("TV IP address, like 192.168.4.37");
        ipBox.setInputType(InputType.TYPE_CLASS_PHONE);
        ipBox.setSingleLine(true);
        SharedPreferences sp = getSharedPreferences("tv", MODE_PRIVATE);
        ipBox.setText(sp.getString("ip", ""));
        root.addView(ipBox);

        installBtn = new Button(this);
        installBtn.setText("Install home screen");
        installBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { start(true); }
        });
        root.addView(installBtn);

        removeBtn = new Button(this);
        removeBtn.setText("Remove it (put the TV back)");
        removeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { start(false); }
        });
        root.addView(removeBtn);

        scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.BLACK);
        logView.setTextSize(14);
        logView.setPadding(0, pad, 0, 0);
        scroll.addView(logView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, lp);

        setContentView(root);
    }

    private void start(final boolean install) {
        if (busy) return;
        final String ip = ipBox.getText().toString().trim();
        if (!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            log("Type the TV's IP address first, like 192.168.4.37");
            return;
        }
        getSharedPreferences("tv", MODE_PRIVATE).edit().putString("ip", ip).apply();
        busy = true;
        installBtn.setEnabled(false);
        removeBtn.setEnabled(false);
        logView.setText("");
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (install) doInstall(ip); else doRemove(ip);
                } catch (Throwable t) {
                    log("Something went wrong: " + t);
                    log("Send a screenshot of this screen.");
                } finally {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            busy = false;
                            installBtn.setEnabled(true);
                            removeBtn.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void log(final String s) {
        runOnUiThread(new Runnable() {
            public void run() {
                logView.append(s + "\n");
                scroll.post(new Runnable() {
                    public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    // ---- adb helpers ----

    private AdbKeyPair keys() throws Exception {
        File priv = new File(getFilesDir(), "adbkey");
        File pub = new File(getFilesDir(), "adbkey.pub");
        if (!priv.exists() || !pub.exists()) {
            AdbKeyPair.generate(priv, pub);
        }
        return AdbKeyPair.read(priv, pub);
    }

    private Dadb connect(String ip) throws Exception {
        AdbKeyPair kp = keys();
        log("Connecting to " + ip + " ...");
        log("WATCH THE TV: if it asks \"Allow debugging?\", tick Always allow and press OK.");
        Exception last = null;
        for (int i = 0; i < 20; i++) {
            Dadb d = null;
            try {
                d = Dadb.create(ip, 5555, kp);
                String r = d.shell("echo ok").getOutput();
                if (r.contains("ok")) {
                    log("Connected.");
                    return d;
                }
            } catch (Exception e) {
                last = e;
            }
            try { if (d != null) d.close(); } catch (Exception ignored) { }
            Thread.sleep(3000);
        }
        throw new Exception("Could not connect. Check the IP, that phone and TV are on the same Wi-Fi, and that Network debugging is ON. " + (last == null ? "" : last.getMessage()));
    }

    private String sh(Dadb d, String cmd) throws Exception {
        return d.shell(cmd).getAllOutput().trim();
    }

    // ---- install ----

    private void doInstall(String ip) throws Exception {
        Dadb d = connect(ip);
        try {
            log("Downloading the home screen app...");
            File apk = new File(getCacheDir(), "tvcleanse-home.apk");
            download(REL + "/tvcleanse-home.apk", apk);
            String want = new String(readAll(REL + "/tvcleanse-home.apk.sha256")).trim().split("\\s+")[0];
            String got = sha256(apk);
            if (!got.equalsIgnoreCase(want)) {
                log("The download did not match its checksum. Stopping to be safe.");
                return;
            }
            log("Download checked OK.");

            log("Installing...");
            try {
                d.install(apk);
            } catch (Exception e) {
                // Most likely a different signing key from an older build: replace it.
                log("Replacing the older version...");
                d.uninstall(PKG);
                d.install(apk);
            }

            sh(d, "cmd role add-role-holder --user 0 android.app.role.HOME " + PKG);

            // Google TV keeps its own Home button, so also turn on our helper.
            // Android 14 needs the app opened once first.
            sh(d, "am start -n " + PKG + "/.MainActivity");
            Thread.sleep(2000);
            String cur = sh(d, "settings get secure enabled_accessibility_services");
            if (cur.equals("null")) cur = "";
            String neu = cur.contains(PKG) ? cur : (cur.isEmpty() ? HELPER : cur + ":" + HELPER);
            sh(d, "settings put secure enabled_accessibility_services " + neu);
            sh(d, "settings put secure accessibility_enabled 1");
            boolean bound = waitBound(d);
            if (!bound) {
                // Android can ignore the setting right after install: switch off and on once.
                sh(d, "settings put secure enabled_accessibility_services '" + cur + "'");
                Thread.sleep(2000);
                sh(d, "settings put secure enabled_accessibility_services " + neu);
                waitBound(d);
            }
            sh(d, "am start -n " + PKG + "/.MainActivity");
            Thread.sleep(2000);
            sh(d, "input keyevent KEYCODE_HOME");

            log("");
            log("DONE. Press Home on the TV remote. You should see TV Cleanse.");
            log("If it still shows the old screen, press Home once more.");
            log("Last step: on the TV, turn USB debugging and Network debugging OFF.");
        } finally {
            try { d.close(); } catch (Exception ignored) { }
        }
    }

    private boolean waitBound(Dadb d) throws Exception {
        for (int i = 0; i < 8; i++) {
            String out = sh(d, "dumpsys accessibility");
            for (String line : out.split("\n")) {
                if (line.contains("Bound services") && line.contains("TV Cleanse Home button")) return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private void doRemove(String ip) throws Exception {
        Dadb d = connect(ip);
        try {
            sh(d, "cmd role remove-role-holder --user 0 android.app.role.HOME " + PKG);
            d.uninstall(PKG);
            sh(d, "input keyevent KEYCODE_HOME");
            log("");
            log("Removed. Your TV is back to its normal home screen.");
        } finally {
            try { d.close(); } catch (Exception ignored) { }
        }
    }

    // ---- download ----

    private byte[] readAll(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    private void download(String url, File out) throws Exception {
        byte[] data = readAll(url);
        try (FileOutputStream fo = new FileOutputStream(out)) {
            fo.write(data);
        }
    }

    private String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest(data)) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
