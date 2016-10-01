package com.mauriciotogneri.fileexplorer.app;

import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;
import com.mauriciotogneri.fileexplorer.models.ButtonBar;
import com.mauriciotogneri.fileexplorer.models.Clipboard;
import com.mauriciotogneri.fileexplorer.models.ToolBar;

import java.util.Stack;

public class MainActivity extends AppCompatActivity
{
    private ToolBar toolBar;
    private ButtonBar buttonBar;
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

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        FolderFragment folderFragment = FolderFragment.newInstance(root);

        addFragment(folderFragment, false);
    }

    public void addFragment(FolderFragment fragment, boolean addToBackStack)
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

        transaction.commit();

        toolBar.update(fragment);
    }

    private void removeFragment(FolderFragment fragment)
    {
        fragments.pop();

        FragmentManager fragmentManager = getSupportFragmentManager();

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_right);
        transaction.remove(fragment);
        transaction.commit();

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
                if (fragments.size() > 1)
                {
                    removeFragment(fragment);
                }
                else
                {
                    finish();
                }
            }
        }
        else
        {
            finish();
        }
    }
}