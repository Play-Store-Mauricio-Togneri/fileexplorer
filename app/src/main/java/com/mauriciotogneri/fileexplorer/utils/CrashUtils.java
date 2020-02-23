package com.mauriciotogneri.fileexplorer.utils;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class CrashUtils
{
    public static void report(Throwable t)
    {
        FirebaseCrashlytics.getInstance().recordException(t);
    }
}