package com.mauriciotogneri.tools;

import com.mauriciotogneri.tools.GenerateAssets.SizeType;

public class ImageSize
{
    public final int width;
    public final int height;

    public ImageSize(int width, int height)
    {
        this.width = width;
        this.height = height;
    }

    public ImageSize bySizeType(SizeType sizeType)
    {
        switch (sizeType)
        {
            case MDPI:
                return mdpi();

            case HDPI:
                return hdpi();

            case XHDPI:
                return xhdpi();

            case XXHDPI:
                return xxhdpi();

            case XXXHDPI:
                return xxxhdpi();
        }

        throw new RuntimeException();
    }

    private ImageSize mdpi()
    {
        return this;
    }

    private ImageSize hdpi()
    {
        return new ImageSize((int) (width * 1.5), (int) (height * 1.5));
    }

    private ImageSize xhdpi()
    {
        return new ImageSize(width * 2, height * 2);
    }

    private ImageSize xxhdpi()
    {
        return new ImageSize(width * 3, height * 3);
    }

    private ImageSize xxxhdpi()
    {
        return new ImageSize(width * 4, height * 4);
    }
}