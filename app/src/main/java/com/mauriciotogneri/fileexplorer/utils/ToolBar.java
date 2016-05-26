package com.mauriciotogneri.fileexplorer.utils;

import android.view.View;
import android.widget.TextView;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;

public class ToolBar
{
    private final TextView folderName;

    public ToolBar(View parent)
    {
        this.folderName = (TextView) parent.findViewById(R.id.folderName);
    }

    public void update(FolderFragment fragment)
    {
        folderName.setText(fragment.folderName());
    }
}