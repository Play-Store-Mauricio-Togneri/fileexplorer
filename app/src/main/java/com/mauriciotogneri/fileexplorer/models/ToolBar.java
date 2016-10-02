package com.mauriciotogneri.fileexplorer.models;

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

    public void update(String title)
    {
        folderName.setText(title);
    }
}