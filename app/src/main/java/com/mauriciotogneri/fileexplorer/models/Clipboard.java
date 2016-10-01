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

    public void paste()
    {
        // TODO

        items.clear();
        mode = Mode.NONE;
    }

    public boolean isEmpty()
    {
        return items.isEmpty();
    }
}