package com.mauriciotogneri.fileexplorer.adapters;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.adapters.StorageAdapter.ViewHolder;
import com.mauriciotogneri.fileexplorer.base.BaseListAdapter;

public class StorageAdapter extends BaseListAdapter<String, ViewHolder>
{
    public StorageAdapter(Context context)
    {
        super(context, R.layout.row_storage);
    }

    @Override
    protected ViewHolder getViewHolder(View view)
    {
        return new ViewHolder(view);
    }

    @Override
    protected void fillView(View rowView, ViewHolder viewHolder, String item)
    {
        viewHolder.name.setText(item);
    }

    protected static class ViewHolder
    {
        public final TextView name;

        public ViewHolder(View view)
        {
            this.name = (TextView) view.findViewById(R.id.name);
        }
    }
}