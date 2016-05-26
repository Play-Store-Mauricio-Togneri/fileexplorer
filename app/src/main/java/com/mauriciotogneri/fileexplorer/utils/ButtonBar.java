package com.mauriciotogneri.fileexplorer.utils;

import android.content.res.Resources;
import android.os.Build;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;

import java.util.Stack;

public class ButtonBar
{
    private final View buttonSelectAll;
    private final View buttonShare;
    private final View buttonDelete;

    public ButtonBar(View parent, final Stack<FolderFragment> fragments)
    {
        this.buttonSelectAll = parent.findViewById(R.id.button_selectAll);
        this.buttonShare = parent.findViewById(R.id.button_share);
        this.buttonDelete = parent.findViewById(R.id.button_delete);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
        {
            fixButtonMargin(parent.getResources(), buttonShare);
            fixButtonMargin(parent.getResources(), buttonDelete);
        }

        this.buttonSelectAll.setVisibility(View.GONE);
        this.buttonSelectAll.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (!fragments.isEmpty())
                {
                    FolderFragment fragment = fragments.peek();
                    fragment.onSelectAll();
                }
            }
        });

        this.buttonShare.setVisibility(View.GONE);
        this.buttonShare.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (!fragments.isEmpty())
                {
                    FolderFragment fragment = fragments.peek();
                    fragment.onShare();
                }
            }
        });

        this.buttonDelete.setVisibility(View.GONE);
        this.buttonDelete.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (!fragments.isEmpty())
                {
                    FolderFragment fragment = fragments.peek();
                    fragment.onDelete();
                }
            }
        });
    }

    public void displayButtons(boolean display, boolean displaySelectAll)
    {
        if (display)
        {
            if (displaySelectAll)
            {
                buttonSelectAll.setVisibility(View.VISIBLE);
            }
            else
            {
                buttonSelectAll.setVisibility(View.GONE);
            }

            buttonShare.setVisibility(View.VISIBLE);
            buttonDelete.setVisibility(View.VISIBLE);
        }
        else
        {
            buttonSelectAll.setVisibility(View.GONE);
            buttonShare.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);
        }
    }

    private void fixButtonMargin(Resources resources, View view)
    {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.setMargins(0, dpToPx(resources, -10), 0, dpToPx(resources, -10));
        view.setLayoutParams(params);
    }

    private int dpToPx(Resources resources, float dp)
    {
        float scale = resources.getDisplayMetrics().density;

        return (int) ((dp * scale) + 0.5f);
    }
}