package com.mauriciotogneri.fileexplorer.app;

import android.app.Application;
import android.os.StrictMode;

import com.mauriciotogneri.fileexplorer.BuildConfig;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(formUri = "http://zeronest.com/acra/report.php")
public class FileExplorer extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

        ACRA.init(this);

        if (BuildConfig.DEBUG)
        {
            StrictMode.ThreadPolicy.Builder threadBuilder = new StrictMode.ThreadPolicy.Builder();
            threadBuilder.detectAll();
            threadBuilder.penaltyLog();
            StrictMode.setThreadPolicy(threadBuilder.build());

            StrictMode.VmPolicy.Builder vmBuilder = new StrictMode.VmPolicy.Builder();
            vmBuilder.detectAll();
            vmBuilder.penaltyLog();
            StrictMode.setVmPolicy(vmBuilder.build());
        }
    }
}