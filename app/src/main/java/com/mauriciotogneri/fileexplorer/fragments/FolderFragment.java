package com.mauriciotogneri.fileexplorer.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.adapters.FolderAdapter;
import com.mauriciotogneri.fileexplorer.app.MainActivity;
import com.mauriciotogneri.fileexplorer.utils.FileInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FolderFragment extends Fragment
{
    private static final String PARAMETER_FOLDER_PATH = "folder_path";

    private MainActivity mainActivity;
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

        listView = (ListView) view.findViewById(R.id.list);
        labelNoItems = (TextView) view.findViewById(R.id.label_noItems);

        return view;
    }

    @Override
    public final void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        List<FileInfo> files = getFileList();
        adapter = new FolderAdapter(mainActivity);

        if (!files.isEmpty())
        {
            adapter.update(files);

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
        }
        else
        {
            listView.setVisibility(View.GONE);
            labelNoItems.setVisibility(View.VISIBLE);
        }
    }

    public synchronized boolean onBackPressed()
    {
        if (adapter.isSelectionMode())
        {
            adapter.unselectAll();
            updateButtonBar();

            return false;
        }
        else
        {
            return true;
        }
    }

    private void updateButtonBar()
    {
        mainActivity.buttonBar().displayButtons(adapter.isSelectionMode(), !adapter.allItemsSelected());
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

        startActivity(Intent.createChooser(intent, getString(R.string.title_openFile)));
    }

    public void onSelectAll()
    {
        adapter.selectAll();
        updateButtonBar();
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
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(fileInfo.mimeType());
        intent.putExtra(Intent.EXTRA_STREAM, fileInfo.uri());

        startActivity(Intent.createChooser(intent, getString(R.string.title_shareFile)));
    }

    private void shareMultiple(List<FileInfo> list)
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

        startActivity(Intent.createChooser(intent, getString(R.string.title_shareFile)));
    }

    public void onDelete()
    {
        final List<FileInfo> selectedItems = adapter.selectedItems(false);

        final Dialog dialog = new Dialog(mainActivity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_waiting);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
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
                dialog.dismiss();

                refreshFolder();

                if (!result)
                {
                    Toast.makeText(mainActivity, R.string.message_errorDeleting, Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
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
    }
}