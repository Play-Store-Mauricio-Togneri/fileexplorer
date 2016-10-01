package com.mauriciotogneri.fileexplorer.models;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileInfo
{
    private final File file;

    private String cachedName = null;
    private String cachedPath = null;
    private String cachedMimeType = null;
    private String cachedExtension = null;
    private String cachedSize = null;
    private Boolean cachedIsImage = null;
    private Boolean cachedIsPdf = null;
    private Boolean cachedIsAudio = null;
    private Boolean cachedIsVideo = null;
    private Boolean cachedIsDirectory = null;
    private Integer cachedNumberOfChildren = null;
    private Bitmap cachedBitmap = null;
    private Uri cachedUri = null;
    private boolean isSelected = false;

    public FileInfo(File file)
    {
        this.file = file;
    }

    public List<FileInfo> files()
    {
        List<FileInfo> result = new ArrayList<>();

        if (isDirectory())
        {
            for (File currentFile : children())
            {
                if (currentFile != null)
                {
                    FileInfo fileInfo = new FileInfo(currentFile);
                    result.addAll(fileInfo.files());
                }
            }
        }
        else
        {
            result.add(this);
        }

        return result;
    }

    public boolean rename(String newName)
    {
        File newFile = new File(file.getParentFile(), newName);

        return !newFile.exists() && file.renameTo(newFile);
    }

    public boolean copy(FileInfo target)
    {
        if (isDirectory())
        {
            boolean allCopied = true;

            for (File currentFile : children())
            {
                if (currentFile != null)
                {
                    FileInfo fileInfo = new FileInfo(currentFile);

                    allCopied &= fileInfo.copy(target);
                }
            }

            return allCopied;
        }
        else
        {
            return copy(file, target.file);
        }
    }

    private boolean copy(File source, File destination)
    {
        File target = new File(destination, source.getName());

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try
        {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(target);
            byte[] buffer = new byte[1024];
            int length;

            while ((length = inputStream.read(buffer)) > 0)
            {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();

            return true;
        }
        catch (Exception e)
        {
            // ignore
            e.printStackTrace();

            return false;
        }
        finally
        {
            close(inputStream);
            close(outputStream);
        }
    }

    private void close(Closeable closeable)
    {
        try
        {
            if (closeable != null)
            {
                closeable.close();
            }
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    public boolean delete()
    {
        if (isDirectory())
        {
            for (File currentFile : children())
            {
                if (currentFile != null)
                {
                    FileInfo fileInfo = new FileInfo(currentFile);
                    fileInfo.delete();
                }
            }
        }

        return file.delete();
    }

    public String name()
    {
        if (cachedName == null)
        {
            cachedName = file.getName();
        }

        return cachedName;
    }

    public Uri uri()
    {
        if (cachedUri == null)
        {
            cachedUri = Uri.fromFile(file);
        }

        return cachedUri;
    }

    public String path()
    {
        if (cachedPath == null)
        {
            cachedPath = file.getAbsolutePath();
        }

        return cachedPath;
    }

    public String mimeType()
    {
        //        String type = null;
        //        String extension = MimeTypeMap.getFileExtensionFromUrl(uri().toString());
        //
        //        if (extension != null)
        //        {
        //            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        //        }

        if (cachedMimeType == null)
        {
            try
            {
                cachedMimeType = URLConnection.guessContentTypeFromName(file.getAbsolutePath());
            }
            catch (Exception e)
            {
                // ignore
            }

            if (cachedMimeType == null)
            {
                cachedMimeType = "text/plain";
            }
        }

        return cachedMimeType;
    }

    public boolean isImage()
    {
        if (cachedIsImage == null)
        {
            String mimeType = mimeType();

            cachedIsImage = (mimeType != null) && mimeType.startsWith("image/");
        }

        return cachedIsImage;
    }

    public boolean isPdf()
    {
        if (cachedIsPdf == null)
        {
            String mimeType = mimeType();

            cachedIsPdf = (mimeType != null) && mimeType.startsWith("application/pdf");
        }

        return cachedIsPdf;
    }

    public boolean isAudio()
    {
        if (cachedIsAudio == null)
        {
            String mimeType = mimeType();

            cachedIsAudio = (mimeType != null) && mimeType.startsWith("audio/");
        }

        return cachedIsAudio;
    }

    public boolean isVideo()
    {
        if (cachedIsVideo == null)
        {
            String mimeType = mimeType();

            cachedIsVideo = (mimeType != null) && mimeType.startsWith("video");
        }

        return cachedIsVideo;
    }

    public boolean isDirectory()
    {
        if (cachedIsDirectory == null)
        {
            cachedIsDirectory = file.isDirectory();
        }

        return cachedIsDirectory;
    }

    public int numberOfChildren()
    {
        if (cachedNumberOfChildren == null)
        {
            cachedNumberOfChildren = children().length;
        }

        return cachedNumberOfChildren;
    }

    private File[] children()
    {
        File[] children = file.listFiles();

        return (children != null) ? children : new File[0];
    }

    public String extension()
    {
        if (cachedExtension == null)
        {
            cachedExtension = "";

            String name = name();

            int index = name.lastIndexOf(".");

            if (index > -1)
            {
                String extension = name.substring(index + 1);

                if (extension.length() <= 4)
                {
                    cachedExtension = extension.toUpperCase();
                }
            }
        }

        return cachedExtension;
    }

    public String size()
    {
        if (cachedSize == null)
        {
            String label = "B";
            double size = file.length();

            if (size > 1024)
            {
                size /= 1024;
                label = "KB";
            }

            if (size > 1024)
            {
                size /= 1024;
                label = "MB";
            }

            if (size > 1024)
            {
                size /= 1024;
                label = "GB";
            }

            if (size % 1 == 0)
            {
                cachedSize = String.format(Locale.getDefault(), "%d %s", (long) size, label);
            }
            else
            {
                cachedSize = String.format(Locale.getDefault(), "%.1f %s", size, label);
            }
        }

        return cachedSize;
    }

    public boolean hasCachedBitmap()
    {
        return (cachedBitmap != null);
    }

    public Bitmap bitmap(int maxSize)
    {
        if (cachedBitmap == null)
        {
            String path = path();

            // decode with inJustDecodeBounds=true to check dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            // calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize);

            // decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;

            cachedBitmap = BitmapFactory.decodeFile(path, options);
        }

        return cachedBitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        // raw height and width of image
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth)
        {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            // calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth)
            {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public boolean toggleSelection()
    {
        isSelected = !isSelected;

        return isSelected;
    }

    public void select(boolean value)
    {
        isSelected = value;
    }

    public boolean isSelected()
    {
        return isSelected;
    }
}