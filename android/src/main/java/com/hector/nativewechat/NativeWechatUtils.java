package com.hector.nativewechat;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NativeWechatUtils {
  private static final OkHttpClient client = new OkHttpClient();

  public static void downloadFileAsBitmap(String url, DownloadBitmapCallback callback) {
    Request request = new Request.Builder().url(url).build();

    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        callback.onFailure(call, e);
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        try (ResponseBody responseBody = response.body()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

          byte[] bytes = responseBody.bytes();

          callback.onResponse(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        }
      }
    });
  }

  public static byte[] bmpToByteArray(final Bitmap bmp, final boolean needRecycle) {
    int i;
    int j;
    if (bmp.getHeight() > bmp.getWidth()) {
      i = bmp.getWidth();
      j = bmp.getWidth();
    } else {
      i = bmp.getHeight();
      j = bmp.getHeight();
    }

    Bitmap localBitmap = Bitmap.createBitmap(i, j, Bitmap.Config.RGB_565);
    Canvas localCanvas = new Canvas(localBitmap);

    while (true) {
      localCanvas.drawBitmap(bmp, new Rect(0, 0, i, j), new Rect(0, 0, i, j), null);
      if (needRecycle)
        bmp.recycle();
      ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
      localBitmap.compress(Bitmap.CompressFormat.JPEG, 100,
        localByteArrayOutputStream);
      localBitmap.recycle();
      byte[] arrayOfByte = localByteArrayOutputStream.toByteArray();
      try {
        localByteArrayOutputStream.close();
        return arrayOfByte;
      } catch (Exception e) {
        //F.out(e);
      }
      i = bmp.getHeight();
      j = bmp.getHeight();
    }
  }

  public static Bitmap compressImage(Bitmap image, Integer size) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
    int options = 100;

    while (baos.toByteArray().length / 1024 > size) {
      // 重置baos即清空baos
      baos.reset();
      if (options > 10) {
        options -= 8;
      } else {
        return compressImage(Bitmap.createScaledBitmap(image, 280, image.getHeight() / image.getWidth() * 280, true), size);
      }
      // 这里压缩options%，把压缩后的数据存放到baos中
      image.compress(Bitmap.CompressFormat.JPEG, options, baos);
    }

    ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
    Bitmap newBitmap = BitmapFactory.decodeStream(isBm, null, null);

    return newBitmap;
  }

  public interface DownloadBitmapCallback {
    void onFailure(@NonNull Call call, @NonNull IOException e);

    void onResponse(@NonNull Bitmap bitmap);
  }

  public interface DownloadFileCallback {
    void onFailure(@NonNull Call call, @NonNull IOException e);

    void onResponse(@NonNull File file);
  }

  public static void downloadToFile(String url, File targetFile, DownloadFileCallback callback) {
    Request request = new Request.Builder().url(url).build();

    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        callback.onFailure(call, e);
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) {
        try (ResponseBody responseBody = response.body()) {
          if (!response.isSuccessful() || responseBody == null) {
            throw new IOException("Unexpected code " + response);
          }

          try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(responseBody.bytes());
            // Ensure the file is fully written before calling back
            fos.getFD().sync();
          }

          // Ensure the UI thread is not blocked if callback does heavy work
          // Consider using a Handler or runOnUiThread if needed for the callback
          callback.onResponse(targetFile);

        } catch (IOException e) {
          // Propagate IOExceptions to the failure callback
          callback.onFailure(call, e);
        } catch (Exception e) {
          // Catch any other unexpected errors during file writing
          callback.onFailure(call, new IOException("Failed to write downloaded file: " + e.getMessage(), e));
        }
      }
    });
  }

  public static Bitmap loadBitmapFromFile(String filePath, int targetSize) {
    try {
      // First, decode with inJustDecodeBounds=true to check dimensions
      final BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(filePath, options);

      // Calculate inSampleSize if needed (similar logic could be used for targetSize)
      // For simplicity, let's just decode the full bitmap for now
      // You might want to add resizing logic here based on targetSize
      // similar to compressImage if needed.
      options.inJustDecodeBounds = false;
      Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);

      // Optional: Compress or resize based on targetSize if necessary
      if (bitmap != null && targetSize > 0) {
        // You could reuse compressImage or implement specific resizing logic
        // For the thumbnail, we need a small bitmap. Let's scale it down if it's large.
        // This is a simple scaling example, adjust as needed.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min((float) targetSize / width, (float) targetSize / height);
        if (scale < 1.0) { // Only scale down
           Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, (int) (width * scale), (int) (height * scale), true);
           if (scaledBitmap != bitmap) { // Avoid recycling if createScaledBitmap returned the original
              bitmap.recycle();
           }
           bitmap = scaledBitmap;
        }
        // Optionally, could compress here too like in compressImage
      }

      return bitmap;
    } catch (OutOfMemoryError oom) {
      Log.e("NativeWechatUtils", "OutOfMemoryError loading bitmap from file: " + filePath, oom);
      return null;
    } catch (Exception e) {
      Log.e("NativeWechatUtils", "Error loading bitmap from file: " + filePath, e);
      return null;
    }
  }
}
