package com.mauriciotogneri.tools;

import java.io.File;
import java.util.Locale;

public class GenerateAssets
{
    private static final String INPUT_ROOT = path("tools", "assets");
    private static final String OUTPUT_ROOT = path("app", "src", "main", "res");

    public enum SizeType
    {
        MDPI, //
        HDPI, //
        XHDPI, //
        XXHDPI, //
        XXXHDPI
    }

    public static void main(String[] args) throws Exception
    {
        GenerateAssets generateAssets = new GenerateAssets();
        generateAssets.cleanFolders();
        generateAssets.processFolder(new File(INPUT_ROOT));
    }

    private void cleanFolders()
    {
        for (SizeType sizeType : SizeType.values())
        {
            File rootFolder = new File(OUTPUT_ROOT, String.format("drawable-%s", sizeType.toString().toLowerCase()));
            cleanFolder(rootFolder);
            rootFolder.mkdir();
        }
    }

    private void process(File inputFile, File outputFolder) throws Exception
    {
        String originalName = inputFile.getName();
        String fileName = originalName.substring(0, originalName.indexOf("-")) + ".png";
        ImageSize baseImageSize = getImageSize(inputFile);

        for (SizeType sizeType : SizeType.values())
        {
            generateImage(inputFile, outputFolder, fileName, baseImageSize, sizeType);
        }
    }

    private void generateImage(File inputFile, File outputFolder, String fileName, ImageSize baseImageSize, SizeType sizeType) throws Exception
    {
        ImageSize imageSize = baseImageSize.bySizeType(sizeType);
        File outputFile = new File(outputFolder, String.format("drawable-%s/%s", sizeType.toString().toLowerCase(), fileName));

        String command = String.format(Locale.getDefault(), "inkscape -z -e %s -w %d -h %d %s", outputFile.getAbsolutePath(), imageSize.width, imageSize.height, inputFile.getAbsolutePath());

        System.out.println(command);
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }

    private ImageSize getImageSize(File file)
    {
        String encodedSize = file.getName().replace(".svg", "").split("-")[1];
        String[] splittedSize = encodedSize.split("h");

        return new ImageSize(Integer.parseInt(splittedSize[0].substring(1)), Integer.parseInt(splittedSize[1]));
    }

    private void processFolder(File root) throws Exception
    {
        if (root.isDirectory())
        {
            //noinspection ConstantConditions
            for (File file : root.listFiles())
            {
                processFolder(file);
            }
        }
        else if (root.isFile())
        {
            process(root, new File(OUTPUT_ROOT));
        }
    }

    private void cleanFolder(File root)
    {
        if (root.isDirectory())
        {
            //noinspection ConstantConditions
            for (File file : root.listFiles())
            {
                cleanFolder(file);
            }

            root.delete();
        }
        else if (root.isFile())
        {
            root.delete();
        }
    }

    private static String path(String... folders)
    {
        StringBuilder builder = new StringBuilder();

        for (String folder : folders)
        {
            if (builder.length() != 0)
            {
                builder.append(File.separator);
            }

            builder.append(folder);
        }

        return builder.toString();
    }
}