package com.svl.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** User media never leaves private app storage. */
public final class WidgetMedia {
    private WidgetMedia() {}
    public static final String PHOTO = "svl_photo.jpg";
    public static final String VIDEO = "svl_video.mp4";
    public static File file(Context context, String name) {
        return new File(context.getFilesDir(), name);
    }
    public static File video(Context context) {
        File target = file(context, VIDEO);
        if (target.isFile() && target.length() > 0) return target;
        return copyBundledIfAvailable(context, "naye_clip.mp4", VIDEO);
    }
    private static File copyBundledIfAvailable(Context context, String asset, String outputName) {
        File target = file(context, outputName);
        try (InputStream in = context.getAssets().open(asset);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            return target;
        } catch (Exception e) {
            if (target.exists()) target.delete();
            return null;
        }
    }
    public static Bitmap photo(Context context, int sidePx) {
        Bitmap source = null;
        File selected = file(context, PHOTO);
        if (selected.isFile()) source = BitmapFactory.decodeFile(selected.getAbsolutePath());
        if (source == null) {
            try (InputStream in = context.getAssets().open("default_photo.jpg")) {
                source = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
        }
        if (source == null) return placeholder(sidePx);
        return square(source, sidePx);
    }
    public static Bitmap square(Bitmap source, int size) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return placeholder(size);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float srcW = source.getWidth(), srcH = source.getHeight();
        float crop = Math.min(srcW, srcH);
        Rect src = new Rect(Math.round((srcW - crop) / 2), Math.round((srcH - crop) / 2),
                Math.round((srcW + crop) / 2), Math.round((srcH + crop) / 2));
        canvas.drawBitmap(source, src, new Rect(0, 0, size, size), paint);
        if (source != output && !source.isRecycled()) source.recycle();
        return output;
    }
    private static Bitmap placeholder(int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(29, 25, 35));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(255, 132, 181));
        canvas.drawRoundRect(new RectF(size * .13f, size * .13f, size * .87f, size * .87f),
                size * .16f, size * .16f, p);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        p.setTextSize(size * .3f);
        canvas.drawText("svl", size * .5f, size * .59f, p);
        return bitmap;
    }
}
