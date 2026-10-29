package com.local.qingmo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

final class ImageStore {
    private static final int MAX_SAVED_EDGE = 1800;
    private static final int JPEG_QUALITY = 88;

    private ImageStore() {
    }

    static String save(Context context, Uri uri) throws IOException {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream exifStream = context.getContentResolver().openInputStream(uri)) {
            if (exifStream != null) {
                ExifInterface exif = new ExifInterface(exifStream);
                orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                );
            }
        } catch (IOException | RuntimeException ignored) {
            // Some document providers do not expose EXIF data; decoding can still proceed.
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("无法读取图片");
            }
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("图片格式不受支持");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("无法读取图片");
            }
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) {
            throw new IOException("图片解码失败");
        }

        Bitmap rotated = applyOrientation(bitmap, orientation);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        Bitmap flattened = flattenTransparency(rotated);
        if (flattened != rotated) {
            rotated.recycle();
        }
        Bitmap scaled = scaleDown(flattened, MAX_SAVED_EDGE);
        if (scaled != flattened) {
            flattened.recycle();
        }

        File directory = mediaDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            scaled.recycle();
            throw new IOException("无法创建图片目录");
        }
        String name = "img_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        File target = new File(directory, name);
        try (FileOutputStream stream = new FileOutputStream(target)) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                throw new IOException("图片压缩失败");
            }
        } catch (IOException error) {
            // Keep an empty partial file from accumulating.
            if (target.exists()) {
                target.delete();
            }
            throw error;
        } finally {
            scaled.recycle();
        }
        return name;
    }

    static Bitmap load(Context context, String name, int maxWidth, int maxHeight) {
        File file = new File(mediaDirectory(context), name);
        if (!file.exists()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateBoundsSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                maxWidth,
                maxHeight
        );
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    static void delete(Context context, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        File file = new File(mediaDirectory(context), name);
        if (file.exists()) {
            file.delete();
        }
    }

    private static File mediaDirectory(Context context) {
        return new File(context.getFilesDir(), "journal-images");
    }

    private static int calculateSampleSize(int width, int height) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / sample > MAX_SAVED_EDGE * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static int calculateBoundsSampleSize(int width, int height, int maxWidth, int maxHeight) {
        int sample = 1;
        if (width <= 0 || height <= 0) {
            return sample;
        }
        while (width / sample > maxWidth && height / sample > maxHeight) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap applyOrientation(Bitmap source, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            default:
                return source;
        }
        try {
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (OutOfMemoryError ignored) {
            return source;
        }
    }

    private static Bitmap scaleDown(Bitmap source, int maxEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxEdge) {
            return source;
        }
        float ratio = maxEdge / (float) longest;
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    private static Bitmap flattenTransparency(Bitmap source) {
        if (!source.hasAlpha()) {
            return source;
        }
        Bitmap flattened = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(flattened);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(source, 0, 0, null);
        return flattened;
    }
}
