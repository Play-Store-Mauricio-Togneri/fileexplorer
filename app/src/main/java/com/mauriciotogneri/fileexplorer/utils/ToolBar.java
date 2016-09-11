package com.mauriciotogneri.fileexplorer.utils;

import android.widget.TextView;

import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;

public class ToolBar
{
    private final TextView folderName;

    public ToolBar(TextView textview)
    {
        this.folderName = textview;
    }

    public void update(FolderFragment fragment)
    {
        folderName.setText(fragment.folderName());
    }
}