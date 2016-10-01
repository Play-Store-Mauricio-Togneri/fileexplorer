package com.mauriciotogneri.fileexplorer.models;

import java.util.ArrayList;
import java.util.List;


public class Clipboard
{
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
        mode = Mode.CUT;
        this.items = items;
    }

    public void copy(List<FileInfo> items)
    {
        mode = Mode.COPY;
        this.items = items;
    }

    public boolean paste(FileInfo target)
    {
        boolean allPasted = true;

        try
        {
            for (FileInfo fileInfo : items)
            {
                allPasted &= fileInfo.copy(target);

                if (mode == Mode.CUT)
                {
                    fileInfo.delete();
                }
            }
        }
        catch (Exception e)
        {
            // ignore
            e.printStackTrace();
        }

        items.clear();
        mode = Mode.NONE;

        return allPasted;
    }

    public boolean isEmpty()
    {
        return items.isEmpty();
    }
}