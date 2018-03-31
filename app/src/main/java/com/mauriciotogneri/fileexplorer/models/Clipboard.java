package com.mauriciotogneri.fileexplorer.models;

import com.mauriciotogneri.fileexplorer.utils.CrashUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Clipboard
{
    private File parent = null;
    private Mode mode = Mode.NONE;
    private List<FileInfo> items = new ArrayList<>();

    private enum Mode
    {
        NONE,
        CUT,
        COPY
    }

    public Clipboard()
    {
    }

    public boolean isCut()
    {
        return mode == Mode.CUT;
    }

    public boolean isCopy()
    {
        return mode == Mode.COPY;
    }

    public void cut(List<FileInfo> items)
    {
        if (!items.isEmpty())
        {
            parent = items.get(0).parent();
        }

        mode = Mode.CUT;
        this.items = items;
    }

    public void copy(List<FileInfo> items)
    {
        if (!items.isEmpty())
        {
            parent = items.get(0).parent();
        }

        mode = Mode.COPY;
        this.items = items;
    }

    public void paste(FileInfo target)
    {
        try
        {
            for (FileInfo fileInfo : items)
            {
                fileInfo.copy(target, mode == Mode.CUT);
            }
        }
        catch (Exception e)
        {
            CrashUtils.report(e);
        }

        items.clear();
        mode = Mode.NONE;
        parent = null;
    }

    public boolean someExist()
    {
        for (FileInfo fileInfo : items)
        {
            if (fileInfo.exists())
            {
                return true;
            }
        }

        return false;
    }

    public boolean isEmpty()
    {
        return items.isEmpty();
    }

    public boolean hasParent(File target)
    {
        for (FileInfo fileInfo : items)
        {
            if (target.getAbsolutePath().startsWith(fileInfo.path()))
            {
                return true;
            }
        }

        return (parent.compareTo(target) == 0);
    }
}