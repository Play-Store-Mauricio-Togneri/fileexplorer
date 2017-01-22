package com.mauriciotogneri.fileexplorer.app;

import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;
import com.mauriciotogneri.fileexplorer.fragments.StorageFragment;
import com.mauriciotogneri.fileexplorer.models.ButtonBar;
import com.mauriciotogneri.fileexplorer.models.Clipboard;
import com.mauriciotogneri.fileexplorer.models.ToolBar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class MainActivity extends AppCompatActivity
{
    private ToolBar toolBar;
    private ButtonBar buttonBar;
    private StorageFragment storageFragment = null;
    private final Stack<FolderFragment> fragments = new Stack<>();
    private final Clipboard clipboard = new Clipboard();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        this.toolBar = new ToolBar((TextView) findViewById(R.id.folderName));
        this.buttonBar = new ButtonBar(findViewById(R.id.buttonBar), fragments);

        String[] storages = storages();

        if (storages.length > 1)
        {
            storageFragment = StorageFragment.newInstance(storages);

            FragmentManager fragmentManager = getSupportFragmentManager();

            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.add(R.id.fragmentContainer, storageFragment);
            transaction.addToBackStack(null);
            transaction.commitAllowingStateLoss();

            toolBar.update(getString(R.string.app_name));
        }
        else
        {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            FolderFragment folderFragment = FolderFragment.newInstance(root);

            addFragment(folderFragment, false);
        }
    }

    private String[] storages()
    {
        List<String> storages = new ArrayList<>();

        try
        {
            File[] externalStorageFiles = ContextCompat.getExternalFilesDirs(this, null);

            String base = String.format("/Android/data/%s/files", getPackageName());

            for (File file : externalStorageFiles)
            {
                try
                {
                    if (file != null)
                    {
                        String path = file.getAbsolutePath();

                        if (path.contains(base))
                        {
                            String finalPath = path.replace(base, "");

                            // check if the stats don't fail
                            StatFs stat = new StatFs(finalPath);
                            stat.getBlockCount();

                            storages.add(finalPath);
                        }
                    }
                }
                catch (Exception e)
                {
                    // ignore
                }
            }
        }
        catch (Exception e)
        {
            // ignore
        }

        String[] result = new String[storages.size()];
        storages.toArray(result);

        return result;
    }

    public void addFragment(final FolderFragment fragment, final boolean addToBackStack)
    {
        fragments.push(fragment);

        FragmentManager fragmentManager = getSupportFragmentManager();

        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (addToBackStack)
        {
            transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_right);
        }

        transaction.add(R.id.fragmentContainer, fragment);

        if (addToBackStack)
        {
            transaction.addToBackStack(null);
        }

        transaction.commitAllowingStateLoss();

        toolBar.update(fragment);
    }

    private void removeFragment(final FolderFragment fragment)
    {
        fragments.pop();

        FragmentManager fragmentManager = getSupportFragmentManager();

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_right);
        transaction.remove(fragment);
        transaction.commitAllowingStateLoss();

        if (!fragments.isEmpty())
        {
            FolderFragment topFragment = fragments.peek();
            topFragment.refreshFolder();

            toolBar.update(topFragment);
        }
    }

    public Clipboard clipboard()
    {
        return clipboard;
    }

    public ButtonBar buttonBar()
    {
        return buttonBar;
    }

    @Override
    public void onBackPressed()
    {
        if (fragments.size() > 0)
        {
            FolderFragment fragment = fragments.peek();

            if (fragment.onBackPressed())
            {
                if (storageFragment == null)
                {
                    if (fragments.size() > 1)
                    {
                        removeFragment(fragment);
                    }
                    else
                    {
                        finish();
                    }
                }
                else
                {
                    removeFragment(fragment);

                    if (fragments.isEmpty())
                    {
                        toolBar.update(getString(R.string.app_name));
                        buttonBar.displayButtons(0, false, false);
                    }
                }
            }
        }
        else
        {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        // no call for super(). Bug on API Level > 11.
    }
}