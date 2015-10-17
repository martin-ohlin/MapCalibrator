package com.Martin.MapCalibrator.misc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

public class Util {

    public static final File mPath = new File(Environment.getExternalStorageDirectory() + "//MapCalibrator//");
    private static final String preferenceFileName = "MapCalibrator";

	public interface OnFileFoundListener {
        public void OnFileFound(File file);
	}
	
	public static void handleSelectedFile(Intent data, Context context, OnFileFoundListener onFileFoundListener) {
        Uri uri = data.getData();
        String mimeType = context.getContentResolver().getType(uri);
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (!extension.equalsIgnoreCase("jpg") &&
                !extension.equalsIgnoreCase("jpeg") &&
                !extension.equalsIgnoreCase("gif") &&
                !extension.equalsIgnoreCase("png") &&
                !extension.equalsIgnoreCase("bmp") &&
                !extension.equalsIgnoreCase("webp")) {
            Toast.makeText(
                    context,
                    "The selected file format is not natively supported in Android", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        File mapFile = saveFile(context, uri, extension);
        onFileFoundListener.OnFileFound(mapFile);
    }

    public static File getNewAvailableFileName(Context context, String extension) {
        SharedPreferences settings = context.getSharedPreferences(preferenceFileName, Context.MODE_PRIVATE);
        File outputFile;

        do {
            int counter = settings.getInt("fileCounter", 1);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("fileCounter", counter + 1);
            editor.apply();
            String fileName = "mapcalibrator_" + counter + "." + extension;
            outputFile = new File(Util.mPath.getAbsolutePath() + File.separatorChar + fileName);
        } while (outputFile.exists());

        return outputFile;
    }

    private static File saveFile(Context context, Uri uri, String extension)
    {
        File outputFile = getNewAvailableFileName(context, extension);

        BufferedInputStream in = null;
        BufferedOutputStream out = null;

        try {
            in = new BufferedInputStream(context.getContentResolver().openInputStream(uri));
            out = new BufferedOutputStream(new FileOutputStream(outputFile, false));

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {

        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {

            }
        }

        return outputFile;
    }

    public static boolean isNewVersion(Context context) {
        SharedPreferences settings = context.getSharedPreferences(preferenceFileName, Context.MODE_PRIVATE);

        int oldVersion = settings.getInt("version", 0);
        int currentVersion = 0;
        PackageInfo pInfo = null;
        try {
            pInfo = context.getPackageManager().getPackageInfo("com.Martin.MapCalibrator", PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
        }
        if (pInfo != null)
            currentVersion = pInfo.versionCode;

        if (currentVersion > oldVersion) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("version", currentVersion);
            editor.apply();
            return true;
        } else {
            return false;
        }
}

    public static boolean isMapSupported(String filePath, Context context) {
		//http://developer.android.com/guide/appendix/media-formats.html
		if (filePath.toLowerCase().endsWith(".jpg") ||
				filePath.toLowerCase().endsWith(".gif") ||
				filePath.toLowerCase().endsWith(".png") ||
				filePath.toLowerCase().endsWith(".bmp") ||
				filePath.toLowerCase().endsWith(".webp")) {
			return true;
		} else {
			Toast.makeText(
					context,
					"The selected file format is not natively supported in android", Toast.LENGTH_LONG)
					.show();
			
			return false;
		}		
	}
}
