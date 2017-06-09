package com.mauriciotogneri.fileexplorer.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.firebase.crash.FirebaseCrash;
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

    public static void rename(Context context, final FileInfo fileInfo, final OnRename callback)
    {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_rename, null);
        final EditText nameField = (EditText) view.findViewById(R.id.item_name);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(view);
        builder.setPositiveButton(R.string.dialog_rename, new OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                callback.rename(fileInfo, nameField.getText().toString());
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
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

        nameField.setOnEditorActionListener(new OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event)
            {
                if (actionId == EditorInfo.IME_ACTION_DONE)
                {
                    try
                    {
                        dialog.dismiss();
                    }
                    catch (Exception e)
                    {
                        FirebaseCrash.report(e);
                    }

                    callback.rename(fileInfo, nameField.getText().toString());
                }

                return false;
            }
        });
    }

    public static void delete(Context context, final FolderAdapter adapter, final OnDelete callback)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setTitle(R.string.delete_confirm);
        builder.setPositiveButton(R.string.dialog_delete, new OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                callback.delete(adapter.selectedItems(false));
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.show();
    }

    public static void create(Context context, final OnCreate callback)
    {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_rename, null);
        final EditText nameField = (EditText) view.findViewById(R.id.item_name);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(view);
        builder.setPositiveButton(R.string.dialog_create, new OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                callback.create(nameField.getText().toString());
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
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

        nameField.setOnEditorActionListener(new OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event)
            {
                if (actionId == EditorInfo.IME_ACTION_DONE)
                {
                    try
                    {
                        dialog.dismiss();
                    }
                    catch (Exception e)
                    {
                        FirebaseCrash.report(e);
                    }

                    callback.create(nameField.getText().toString());
                }

                return false;
            }
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