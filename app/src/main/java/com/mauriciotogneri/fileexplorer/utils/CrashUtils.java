package com.mauriciotogneri.fileexplorer.utils;

import com.crashlytics.android.Crashlytics;

public class CrashUtils
{
    public static void report(Throwable t)
    {
        Crashlytics.logException(t);
    }
}