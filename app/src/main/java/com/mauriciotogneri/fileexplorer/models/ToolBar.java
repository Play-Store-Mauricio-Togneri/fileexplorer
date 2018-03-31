package com.mauriciotogneri.fileexplorer.models;

import android.widget.TextView;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;
import com.mauriciotogneri.fileexplorer.utils.CrashUtils;

public class ToolBar
{
    private final TextView folderName;

    public ToolBar(TextView textview)
    {
        this.folderName = textview;
    }

    public void update(FolderFragment fragment)
    {
        updateTitle(fragment.folderName());
    }

    public void update(String title)
    {
        updateTitle(title);
    }

    private void updateTitle(String text)
    {
        try
        {
            folderName.setText(text);
        }
        catch (Exception e)
        {
            CrashUtils.report(e);

            folderName.setText(R.string.app_name);
        }
    }
}