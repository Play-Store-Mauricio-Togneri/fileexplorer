package com.mauriciotogneri.fileexplorer.utils;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.adapters.FolderAdapter;
import com.mauriciotogneri.fileexplorer.models.FileInfo;

import java.util.List;

public class Dialogs
{
    private Dialogs()
    {
    }

    public static ProgressDialog progress(Context context, String message)
    {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        return dialog;
    }

    @SuppressLint("InflateParams")
    public static void rename(Context context, FileInfo fileInfo, OnRename callback)
    {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_rename, null);
        EditText nameField = view.findViewById(R.id.item_name);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(view);
        builder.setPositiveButton(R.string.dialog_rename, (dialogInterface, i) -> callback.rename(fileInfo, nameField.getText().toString()));
        builder.setNegativeButton(R.string.dialog_cancel, null);

        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();

        if (window != null)
        {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        dialog.show();

        String name = fileInfo.name();

        nameField.setText(name);
        nameField.requestFocus();

        int dotIndex = name.lastIndexOf(".");

        if (dotIndex != -1)
        {
            nameField.setSelection(0, dotIndex);
        }
        else
        {
            nameField.selectAll();
        }

        nameField.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void afterTextChanged(Editable text)
            {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(text.length() != 0);
            }
        });

        nameField.setOnEditorActionListener((view1, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE)
            {
                try
                {
                    dialog.dismiss();
                }
                catch (Exception e)
                {
                    CrashUtils.report(e);
                }

                callback.rename(fileInfo, nameField.getText().toString());
            }

            return false;
        });
    }

    public static void delete(Context context, FolderAdapter adapter, OnDelete callback)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setTitle(R.string.delete_confirm);
        builder.setPositiveButton(R.string.dialog_delete, (dialogInterface, i) -> callback.delete(adapter.selectedItems(false)));
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.show();
    }

    @SuppressLint("InflateParams")
    public static void create(Context context, OnCreate callback)
    {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_rename, null);
        EditText nameField = view.findViewById(R.id.item_name);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(view);
        builder.setPositiveButton(R.string.dialog_create, (dialogInterface, i) -> callback.create(nameField.getText().toString()));
        builder.setNegativeButton(R.string.dialog_cancel, null);

        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();

        if (window != null)
        {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        nameField.requestFocus();
        nameField.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void afterTextChanged(Editable text)
            {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(text.length() != 0);
            }
        });

        nameField.setOnEditorActionListener((view1, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE)
            {
                try
                {
                    dialog.dismiss();
                }
                catch (Exception e)
                {
                    CrashUtils.report(e);
                }

                callback.create(nameField.getText().toString());
            }

            return false;
        });
    }

    public interface OnRename
    {
        void rename(FileInfo fileInfo, String newName);
    }

    public interface OnDelete
    {
        void delete(List<FileInfo> selectedItems);
    }

    public interface OnCreate
    {
        void create(String name);
    }
}