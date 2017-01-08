package com.mauriciotogneri.fileexplorer.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.adapters.FolderAdapter;
import com.mauriciotogneri.fileexplorer.app.MainActivity;
import com.mauriciotogneri.fileexplorer.models.Clipboard;
import com.mauriciotogneri.fileexplorer.models.FileInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FolderFragment extends Fragment
{
    private static final String PARAMETER_FOLDER_PATH = "folder.path";

    private MainActivity mainActivity;
    private SwipeRefreshLayout swipeContainer;
    private ListView listView;
    private TextView labelNoItems;
    private FolderAdapter adapter;

    public static FolderFragment newInstance(String folderPath)
    {
        FolderFragment fragment = new FolderFragment();
        Bundle parameters = new Bundle();
        parameters.putSerializable(PARAMETER_FOLDER_PATH, folderPath);
        fragment.setArguments(parameters);

        return fragment;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        mainActivity = (MainActivity) context;
    }

    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.screen_folder, container, false);

        swipeContainer = (SwipeRefreshLayout) view.findViewById(R.id.swipeContainer);
        listView = (ListView) view.findViewById(R.id.list);
        labelNoItems = (TextView) view.findViewById(R.id.label_noItems);

        return view;
    }

    @Override
    public final void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        swipeContainer.setColorSchemeResources(R.color.blue1);
        swipeContainer.setOnRefreshListener(new OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                refreshFolder();
                swipeContainer.setRefreshing(false);
            }
        });

        adapter = new FolderAdapter(mainActivity);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                FileInfo fileInfo = (FileInfo) parent.getItemAtPosition(position);

                if (adapter.isSelectionMode())
                {
                    adapter.updateSelection(fileInfo.toggleSelection());
                    updateButtonBar();
                }
                else
                {
                    if (fileInfo.isDirectory())
                    {
                        openFolder(fileInfo);
                    }
                    else
                    {
                        openFile(fileInfo);
                    }
                }
            }
        });

        listView.setOnItemLongClickListener(new OnItemLongClickListener()
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
            {
                FileInfo fileInfo = (FileInfo) parent.getItemAtPosition(position);
                adapter.updateSelection(fileInfo.toggleSelection());
                updateButtonBar();

                return true;
            }
        });

        refreshFolder();
    }

    public synchronized boolean onBackPressed()
    {
        if ((adapter != null) && adapter.isSelectionMode())
        {
            unselectAll();

            return false;
        }
        else
        {
            return true;
        }
    }

    private void unselectAll()
    {
        adapter.unselectAll();
        updateButtonBar();
    }

    private void updateButtonBar()
    {
        Clipboard clipboard = mainActivity.clipboard();

        mainActivity.buttonBar().displayButtons(adapter.itemsSelected(), !adapter.allItemsSelected(), !clipboard.isEmpty() && !clipboard.hasParent(folder()));
    }

    public String folderName()
    {
        return folder().getAbsolutePath();
    }

    private File folder()
    {
        String folderPath = getParameter(PARAMETER_FOLDER_PATH, "/");

        return new File(folderPath);
    }

    private List<FileInfo> getFileList()
    {
        File root = folder();
        File[] fileArray = root.listFiles();

        if (fileArray != null)
        {
            List<File> files = Arrays.asList(fileArray);

            Collections.sort(files, new Comparator<File>()
            {
                @Override
                public int compare(File lhs, File rhs)
                {
                    if (lhs.isDirectory() && !rhs.isDirectory())
                    {
                        return -1;
                    }
                    else if (!lhs.isDirectory() && rhs.isDirectory())
                    {
                        return 1;
                    }
                    else
                    {
                        return lhs.getName().toLowerCase().compareTo(rhs.getName().toLowerCase());
                    }
                }
            });

            List<FileInfo> result = new ArrayList<>();

            for (File file : files)
            {
                if (file != null)
                {
                    result.add(new FileInfo(file));
                }
            }

            return result;
        }
        else
        {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private <Type> Type getParameter(String key, Type defaultValue)
    {
        Bundle extras = getArguments();

        if ((extras != null) && extras.containsKey(key))
        {
            return (Type) extras.get(key);
        }
        else
        {
            return defaultValue;
        }
    }

    private void openFolder(FileInfo fileInfo)
    {
        FolderFragment folderFragment = FolderFragment.newInstance(fileInfo.path());

        mainActivity.addFragment(folderFragment, true);
    }

    private void openFile(FileInfo fileInfo)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileInfo.uri(), fileInfo.mimeType());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager manager = getContext().getPackageManager();
        List<ResolveInfo> resolveInfo = manager.queryIntentActivities(intent, 0);

        if (resolveInfo.size() > 0)
        {
            try
            {
                startActivity(intent);
            }
            catch (Exception e)
            {
                Toast.makeText(getContext(), R.string.open_unable, Toast.LENGTH_SHORT).show();
            }
        }
        else
        {
            Toast.makeText(getContext(), R.string.open_unable, Toast.LENGTH_SHORT).show();
        }
    }

    public void onCut()
    {
        List<FileInfo> items = adapter.selectedItems(false);
        mainActivity.clipboard().cut(items);
        unselectAll();
    }

    public void onCopy()
    {
        List<FileInfo> items = adapter.selectedItems(false);
        mainActivity.clipboard().copy(items);
        unselectAll();
    }

    public void onPaste()
    {
        final Clipboard clipboard = mainActivity.clipboard();

        String message = "";

        if (clipboard.isCut())
        {
            message = getString(R.string.clipboard_cut);
        }
        else if (clipboard.isCopy())
        {
            message = getString(R.string.clipboard_copy);
        }

        final ProgressDialog dialog = new ProgressDialog(getContext());
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                clipboard.paste(new FileInfo(folder()));

                return null;
            }

            @Override
            protected void onPostExecute(Void result)
            {
                try
                {
                    dialog.dismiss();
                }
                catch (Exception e)
                {
                    // ignore
                }

                refreshFolder();
            }
        }.execute();
    }

    public void onSelectAll()
    {
        adapter.selectAll();
        updateButtonBar();
    }

    public void onRename()
    {
        List<FileInfo> items = adapter.selectedItems(false);

        if (items.size() == 1)
        {
            final FileInfo fileInfo = items.get(0);

            View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_rename, null);
            final EditText nameField = (EditText) view.findViewById(R.id.item_name);

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setCancelable(false);
            builder.setView(view);
            builder.setPositiveButton(R.string.dialog_rename, new OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialogInterface, int i)
                {
                    renameItem(fileInfo, nameField.getText().toString());
                }
            });
            builder.setNegativeButton(R.string.dialog_cancel, null);

            final AlertDialog dialog = builder.create();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            dialog.show();

            nameField.setText(fileInfo.name());
            nameField.requestFocus();

            int dotIndex = fileInfo.name().lastIndexOf(".");

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
                            // ignore
                        }

                        renameItem(fileInfo, nameField.getText().toString());
                    }

                    return false;
                }
            });
        }
    }

    public void onShare()
    {
        List<FileInfo> selectedItems = adapter.selectedItems(true);

        if (selectedItems.size() == 1)
        {
            shareSingle(selectedItems.get(0));
        }
        else if (!selectedItems.isEmpty())
        {
            shareMultiple(selectedItems);
        }
    }

    private void shareSingle(FileInfo fileInfo)
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(fileInfo.mimeType());
            intent.putExtra(Intent.EXTRA_STREAM, fileInfo.uri());

            startActivity(Intent.createChooser(intent, getString(R.string.shareFile_title)));
        }
        catch (Exception e)
        {
            Toast.makeText(getContext(), R.string.shareFile_unable, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareMultiple(List<FileInfo> list)
    {
        try
        {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
            intent.setType(list.get(0).mimeType());

            ArrayList<Uri> files = new ArrayList<>();

            for (FileInfo fileInfo : list)
            {
                files.add(fileInfo.uri());
            }

            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);

            startActivity(Intent.createChooser(intent, getString(R.string.shareFile_title)));
        }
        catch (Exception e)
        {
            Toast.makeText(getContext(), R.string.shareFiles_unable, Toast.LENGTH_SHORT).show();
        }
    }

    public void onDelete()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setCancelable(false);
        builder.setTitle(R.string.delete_confirm);
        builder.setPositiveButton(R.string.dialog_delete, new OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                deleteSelected(adapter.selectedItems(false));
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.show();
    }

    private void deleteSelected(final List<FileInfo> selectedItems)
    {
        final ProgressDialog dialog = new ProgressDialog(getContext());
        dialog.setMessage(getString(R.string.delete_deleting));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        new AsyncTask<Void, Void, Boolean>()
        {
            @Override
            protected Boolean doInBackground(Void... params)
            {
                boolean allDeleted = true;

                for (FileInfo fileInfo : selectedItems)
                {
                    if (!fileInfo.delete())
                    {
                        allDeleted = false;
                    }
                }

                return allDeleted;
            }

            @Override
            protected void onPostExecute(Boolean result)
            {
                try
                {
                    dialog.dismiss();
                }
                catch (Exception e)
                {
                    // ignore
                }

                refreshFolder();

                if (!result)
                {
                    Toast.makeText(getContext(), R.string.delete_error, Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void renameItem(FileInfo fileInfo, String newName)
    {
        if (fileInfo.rename(newName))
        {
            refreshFolder();
        }
        else
        {
            Toast.makeText(getContext(), R.string.rename_error, Toast.LENGTH_SHORT).show();
        }
    }

    public void refreshFolder()
    {
        List<FileInfo> files = getFileList();
        adapter.setData(files);
        updateButtonBar();

        if (files.isEmpty())
        {
            listView.setVisibility(View.GONE);
            labelNoItems.setVisibility(View.VISIBLE);
        }
        else
        {
            listView.setVisibility(View.VISIBLE);
            labelNoItems.setVisibility(View.GONE);
        }
    }
}