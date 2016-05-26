package com.mauriciotogneri.fileexplorer.app;

import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.fragments.FolderFragment;
import com.mauriciotogneri.fileexplorer.utils.ButtonBar;
import com.mauriciotogneri.fileexplorer.utils.ToolBar;

import java.util.Stack;

public class MainActivity extends FragmentActivity
{
    private ToolBar toolBar;
    private ButtonBar buttonBar;
    private final Stack<FolderFragment> fragments = new Stack<>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_main);

        this.toolBar = new ToolBar(findViewById(R.id.toolBar));
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

        transaction.add(R.id.fragment_container, fragment);

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

    public ButtonBar buttonBar()
    {
        return buttonBar;
    }

    @Override
    public void onBackPressed()
    {
        if (fragments.size() > 1)
        {
            FolderFragment fragment = fragments.peek();

            if (fragment.onBackPressed())
            {
                removeFragment(fragment);
            }
        }
        else
        {
            finish();
        }
    }
}