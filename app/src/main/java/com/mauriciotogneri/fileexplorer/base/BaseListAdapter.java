package com.mauriciotogneri.fileexplorer.base;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseListAdapter<T, V> extends ArrayAdapter<T>
{
    private final int resourceId;

    protected BaseListAdapter(Context context, int resourceId)
    {
        super(context, resourceId, new ArrayList<>());

        this.resourceId = resourceId;
    }

    protected abstract V viewHolder(View view);

    protected abstract void fillView(View rowView, V viewHolder, T item);

    public void update(List<T> list)
    {
        clear();
        addAll(list);
        notifyDataSetChanged();
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public View getView(int position, View convertView, @NonNull ViewGroup parent)
    {
        V viewHolder;

        View rowView = convertView;

        if (rowView == null)
        {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            rowView = inflater.inflate(resourceId, parent, false);

            viewHolder = viewHolder(rowView);
            rowView.setTag(viewHolder);
        }
        else
        {
            viewHolder = (V) rowView.getTag();
        }

        T item = getItem(position);

        fillView(rowView, viewHolder, item);

        return rowView;
    }
}