package com.mauriciotogneri.fileexplorer.app;

import android.app.Application;
import android.os.StrictMode;

import com.google.firebase.crash.FirebaseCrash;
import com.mauriciotogneri.fileexplorer.BuildConfig;

import java.lang.Thread.UncaughtExceptionHandler;

public class FileExplorer extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler());

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

    public class CustomExceptionHandler implements UncaughtExceptionHandler
    {
        private final UncaughtExceptionHandler defaultHandler;

        public CustomExceptionHandler()
        {
            this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable)
        {
            FirebaseCrash.report(throwable);

            defaultHandler.uncaughtException(thread, throwable);
        }
    }
}