package com.svl.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
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
import java.io.IOException;

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

    /**
     * Use EXIF to transform selected Samsung Gallery photos into upright pixels.
     * Existing saved photos are corrected at display time; new uploads are stored
     * as normalized JPEGs without a rotation tag.
     */
    public static Bitmap photo(Context context, int sidePx) {
        Bitmap source = null;
        File selected = file(context, PHOTO);
        if (selected.isFile()) source = decodeUpright(selected, Math.max(640, sidePx * 2));
        if (source == null) {
            try (InputStream in = context.getAssets().open("default_photo.jpg")) {
                source = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
        }
        if (source == null) return placeholder(sidePx);
        Bitmap cropped = square(source, sidePx);
        int quarterTurns = context.getSharedPreferences("svl", Context.MODE_PRIVATE)
                .getInt("photo_manual_quarter_turns", 0) % 4;
        if (quarterTurns == 0) return cropped;
        Matrix matrix = new Matrix();
        matrix.setRotate(90f * quarterTurns);
        Bitmap rotated = Bitmap.createBitmap(cropped, 0, 0,
                cropped.getWidth(), cropped.getHeight(), matrix, true);
        cropped.recycle();
        return rotated;
    }

    /** Exactly one clockwise quarter-turn, only when the user taps the button. */
    public static void rotatePhoto90(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("svl", Context.MODE_PRIVATE);
        int turns = prefs.getInt("photo_manual_quarter_turns", 0);
        prefs.edit().putInt("photo_manual_quarter_turns", (turns + 1) % 4).apply();
    }

    /** Persist a square, upright JPEG; do not preserve misleading EXIF rotation metadata. */
    public static void importPhoto(Context context, Uri uri) throws IOException {
        File temporary = file(context, "svl_photo_import.tmp");
        File staged = file(context, "svl_photo_staged.jpg");
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(temporary)) {
                if (in == null) throw new IOException("사진을 읽을 수 없어요.");
                byte[] bytes = new byte[8192];
                int count;
                while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            }
            Bitmap oriented = decodeUpright(temporary, 1600);
            if (oriented == null) throw new IOException("지원하지 않는 사진 형식이에요.");
            Bitmap centered = square(oriented, 1000);
            try (OutputStream out = new FileOutputStream(staged)) {
                if (!centered.compress(Bitmap.CompressFormat.JPEG, 93, out))
                    throw new IOException("사진 저장에 실패했어요.");
            } finally {
                centered.recycle();
            }
            File dest = file(context, PHOTO);
            if (!staged.renameTo(dest)) {
                try (InputStream in = new java.io.FileInputStream(staged);
                     OutputStream out = new FileOutputStream(dest)) {
                    byte[] bytes = new byte[8192];
                    int count;
                    while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
                }
            }
            context.getSharedPreferences("svl", Context.MODE_PRIVATE)
                    .edit().putInt("photo_manual_quarter_turns", 0).apply();
        } finally {
            temporary.delete();
            staged.delete();
        }
    }

    private static Bitmap decodeUpright(File file, int maxDimensionPx) {
        Bitmap source = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / opts.inSampleSize > maxDimensionPx) opts.inSampleSize *= 2;
            source = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (source == null) return null;

            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            Matrix transform = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    transform.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    transform.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    transform.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    transform.setRotate(90); transform.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    transform.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    transform.setRotate(270); transform.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    transform.setRotate(270); break;
                default: return source;
            }
            Bitmap normalized = Bitmap.createBitmap(source, 0, 0,
                    source.getWidth(), source.getHeight(), transform, true);
            if (normalized != source) source.recycle();
            return normalized;
        } catch (Exception e) {
            // Some gallery formats have no EXIF tag; use their decoded pixels.
            return source;
        }
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
