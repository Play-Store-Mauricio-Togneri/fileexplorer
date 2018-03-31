package com.mauriciotogneri.fileexplorer.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.adapters.StorageAdapter;
import com.mauriciotogneri.fileexplorer.app.MainActivity;

import java.util.Arrays;

public class StorageFragment extends Fragment
{
    private static final String PARAMETER_STORAGES_PATH = "storages.path";

    private MainActivity mainActivity;
    private ListView listView;
    private StorageAdapter adapter;

    public static StorageFragment newInstance(String[] storagesPath)
    {
        StorageFragment fragment = new StorageFragment();
        Bundle parameters = new Bundle();
        parameters.putStringArray(PARAMETER_STORAGES_PATH, storagesPath);
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
        View view = inflater.inflate(R.layout.screen_storage, container, false);

        listView = view.findViewById(R.id.list);

        return view;
    }

    @Override
    public final void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        adapter = new StorageAdapter(mainActivity);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String storagePath = (String) parent.getItemAtPosition(position);
            openStorage(storagePath);
        });

        reload();
    }

    public void reload()
    {
        adapter.update(Arrays.asList(storages()));
    }

    private String[] storages()
    {
        Bundle extras = getArguments();

        return (extras != null) ? extras.getStringArray(PARAMETER_STORAGES_PATH) : new String[0];
    }

    private void openStorage(String storagePath)
    {
        FolderFragment folderFragment = FolderFragment.newInstance(storagePath);

        mainActivity.addFragment(folderFragment, true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        // no call for super(). Bug on API Level > 11.
    }
}