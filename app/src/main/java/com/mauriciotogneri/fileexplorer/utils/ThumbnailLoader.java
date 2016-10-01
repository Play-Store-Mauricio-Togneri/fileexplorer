package com.mauriciotogneri.fileexplorer.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.mauriciotogneri.fileexplorer.R;
import com.mauriciotogneri.fileexplorer.models.FileInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbnailLoader
{
    private final int maxSize;
    private final ExecutorService threadPool;
    private static final int MAX_DP = 24;

    public ThumbnailLoader(Resources resources)
    {
        this.maxSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MAX_DP, resources.getDisplayMetrics());
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    public void load(final FileInfo fileInfo, final ImageView imageView)
    {
        if (fileInfo.hasCachedBitmap())
        {
            imageView.setImageBitmap(fileInfo.bitmap(maxSize));
        }
        else
        {
            threadPool.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    final Bitmap bitmap = fileInfo.bitmap(maxSize);

                    imageView.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            imageView.setImageBitmap(bitmap);

                            Animation animation = AnimationUtils.loadAnimation(imageView.getContext(), R.anim.fadein);
                            imageView.startAnimation(animation);
                        }
                    });
                }
            });
        }
    }
}