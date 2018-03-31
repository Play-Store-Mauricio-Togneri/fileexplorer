package com.mauriciotogneri.fileexplorer.models;

import android.content.res.Resources;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;

import java.util.Stack;

public class ButtonBar
{
    private final View buttonCut;
    private final View buttonCopy;
    private final View buttonPaste;
    private final View buttonSelectAll;
    private final View buttonRename;
    private final View buttonShare;
    private final View buttonDelete;
    private final View buttonCreate;

    public ButtonBar(View parent, Stack<FolderFragment> fragments)
    {
        this.buttonCut = parent.findViewById(R.id.button_cut);
        this.buttonCopy = parent.findViewById(R.id.button_copy);
        this.buttonPaste = parent.findViewById(R.id.button_paste);
        this.buttonSelectAll = parent.findViewById(R.id.button_selectAll);
        this.buttonRename = parent.findViewById(R.id.button_rename);
        this.buttonShare = parent.findViewById(R.id.button_share);
        this.buttonDelete = parent.findViewById(R.id.button_delete);
        this.buttonCreate = parent.findViewById(R.id.button_create);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
        {
            fixButtonMargin(parent.getResources(), buttonCut);
            fixButtonMargin(parent.getResources(), buttonCopy);
            fixButtonMargin(parent.getResources(), buttonPaste);
            fixButtonMargin(parent.getResources(), buttonSelectAll);
            fixButtonMargin(parent.getResources(), buttonRename);
            fixButtonMargin(parent.getResources(), buttonShare);
            fixButtonMargin(parent.getResources(), buttonDelete);
            fixButtonMargin(parent.getResources(), buttonCreate);
        }

        this.buttonCut.setVisibility(View.GONE);
        this.buttonCut.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onCut();
            }
        });

        this.buttonCopy.setVisibility(View.GONE);
        this.buttonCopy.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onCopy();
            }
        });

        this.buttonPaste.setVisibility(View.GONE);
        this.buttonPaste.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onPaste();
            }
        });

        this.buttonSelectAll.setVisibility(View.GONE);
        this.buttonSelectAll.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onSelectAll();
            }
        });

        this.buttonRename.setVisibility(View.GONE);
        this.buttonRename.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onRename();
            }
        });

        this.buttonShare.setVisibility(View.GONE);
        this.buttonShare.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onShare();
            }
        });

        this.buttonDelete.setVisibility(View.GONE);
        this.buttonDelete.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onDelete();
            }
        });

        this.buttonCreate.setVisibility(View.GONE);
        this.buttonCreate.setOnClickListener(view -> {
            if (!fragments.isEmpty())
            {
                FolderFragment fragment = fragments.peek();
                fragment.onCreate();
            }
        });
    }

    public void displayButtons(int itemsSelected, boolean displaySelectAll, boolean displayPaste, boolean displayShare, boolean displayCreate)
    {
        if (itemsSelected > 0)
        {
            if (displaySelectAll)
            {
                buttonSelectAll.setVisibility(View.VISIBLE);
            }
            else
            {
                buttonSelectAll.setVisibility(View.GONE);
            }

            if (itemsSelected == 1)
            {
                buttonRename.setVisibility(View.VISIBLE);
            }
            else
            {
                buttonRename.setVisibility(View.GONE);
            }

            buttonCut.setVisibility(View.VISIBLE);
            buttonCopy.setVisibility(View.VISIBLE);
            buttonDelete.setVisibility(View.VISIBLE);

            if (displayShare)
            {
                buttonShare.setVisibility(View.VISIBLE);
            }
        }
        else
        {
            if (displayPaste)
            {
                buttonPaste.setVisibility(View.VISIBLE);
            }
            else
            {
                buttonPaste.setVisibility(View.GONE);
            }

            buttonCut.setVisibility(View.GONE);
            buttonCopy.setVisibility(View.GONE);
            buttonSelectAll.setVisibility(View.GONE);
            buttonRename.setVisibility(View.GONE);
            buttonShare.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);
        }

        if (displayCreate)
        {
            if (itemsSelected > 0)
            {
                buttonCreate.setVisibility(View.GONE);
            }
            else
            {
                buttonCreate.setVisibility(View.VISIBLE);
            }
        }
        else
        {
            buttonCreate.setVisibility(View.GONE);
        }
    }

    private void fixButtonMargin(Resources resources, View view)
    {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.setMargins(0, dpToPx(resources, -15), dpToPx(resources, -10), dpToPx(resources, -10));
        view.setLayoutParams(params);
    }

    private int dpToPx(Resources resources, float dp)
    {
        float scale = resources.getDisplayMetrics().density;

        return (int) ((dp * scale) + 0.5f);
    }
}