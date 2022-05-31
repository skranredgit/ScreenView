package com.example.test5;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.DataOutputStream;
import java.io.IOException;

public class MainStarter {

    private Context context;

    public MainStarter(Context context) {
        this.context = context;
    }

    public void start() {
        try {
            Main.main();
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.flush();
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String getApkLocation() {
        PackageManager pm = context.getPackageManager();

        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
//            Log.d("PackageList", "package: " + app.packageName + ", sourceDir: " + app.sourceDir);
            if (app.packageName.equals(context.getPackageName())) {
                return app.sourceDir;
            }
        }
        return null;
    }
}
