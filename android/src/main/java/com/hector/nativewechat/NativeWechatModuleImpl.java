package com.hector.nativewechat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.content.FileProvider;
import android.net.Uri;
import java.io.File;
import java.util.UUID;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelbiz.SubscribeMessage;
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram;
import com.tencent.mm.opensdk.modelbiz.WXOpenCustomerServiceChat;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXImageObject;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXMiniProgramObject;
import com.tencent.mm.opensdk.modelmsg.WXTextObject;
import com.tencent.mm.opensdk.modelmsg.WXVideoObject;
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;

public class NativeWechatModuleImpl implements IWXAPIEventHandler {
  public static final String NAME = "Wechat";
  private static final String REDIRECT_INTENT_ACTION = "com.hector.nativewechat.ACTION_REDIRECT_INTENT";

  private static ReactApplicationContext reactContext;
  private static String appid;
  private static Boolean registered = false;
  private static IWXAPI wxApi;
  private static NativeWechatModuleImpl instance;

  public NativeWechatModuleImpl(ReactApplicationContext context) {
    reactContext = context;
    instance = this;

    if (!registered) {
      compatRegisterReceiver(
        context,
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            handleIntent((Intent)intent.getExtras().get("intent"));
          }
        },
        new IntentFilter(REDIRECT_INTENT_ACTION),
        true
      );
    }
  }

  /**
   * Starting with Android 14, apps and services that target Android 14 and use
   * context-registered receivers are required to specify a flag to indicate
   * whether or not the receiver should be exported to all other apps on the
   * device: either RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED
   *
   * @see https://developer.android.com/about/versions/14/behavior-changes-14#runtime-receivers-exported
   * @see https://github.com/react-native-share/react-native-share/issues/1463
   */
  private void compatRegisterReceiver(
    Context context,
    BroadcastReceiver receiver,
    IntentFilter filter,
    boolean exported
  ) {
    if (Build.VERSION.SDK_INT >= 34 &&
        context.getApplicationInfo().targetSdkVersion >= 34) {
      context.registerReceiver(
        receiver,
        filter,
        exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED
      );
    } else {
      context.registerReceiver(receiver, filter);
    }
  }

  public static Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("WXSceneSession", SendMessageToWX.Req.WXSceneSession);
    constants.put("WXSceneTimeline", SendMessageToWX.Req.WXSceneTimeline);
    constants.put("WXSceneFavorite", SendMessageToWX.Req.WXSceneFavorite);
    constants.put("WXMiniProgramTypeRelease", WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE);
    constants.put("WXMiniProgramTypeTest", WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_TEST);
    constants.put("WXMiniProgramTypePreview", WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_PREVIEW);

    return constants;
  }

  public static IWXAPI getApiInstance() throws Exception {
    if (wxApi == null)
      throw new Exception("Registration is required");

    return wxApi;
  }

  public static void handleIntent(Intent intent) {
    wxApi.handleIntent(intent, instance);
  }

  public void registerApp(ReadableMap request) {
    appid = request.getString("appid");
    registered = true;

    wxApi = WXAPIFactory.createWXAPI(reactContext, appid, true);
    wxApi.registerApp(appid);
  }

  public void isWechatInstalled(Callback callback) {
    callback.invoke(null, wxApi.isWXAppInstalled());
  }

  public void sendAuthRequest(ReadableMap request, Callback callback) {
    SendAuth.Req req = new SendAuth.Req();

    req.scope = request.getString("scope");
    req.state = request.getString("state");

    callback.invoke(wxApi.sendReq(req) ? null : true);
  }

  public void shareText(ReadableMap request, Callback callback) {
    String text = request.getString("text");
    int scene = request.getInt("scene");

    WXTextObject textObj = new WXTextObject();
    textObj.text = text;

    WXMediaMessage msg = new WXMediaMessage();
    msg.mediaObject = textObj;
    msg.description = text;

    SendMessageToWX.Req req = new SendMessageToWX.Req();
    req.message = msg;
    req.scene = scene;

    callback.invoke(wxApi.sendReq(req) ? null : true);
  }

  public void shareImage(ReadableMap request, Callback callback) {
    String url = request.getString("src");
    int scene = request.getInt("scene");

    if (checkAndroidNotBelowN() && checkVersionValid()) {
      try {
        File shareDir = new File(reactContext.getExternalFilesDir(null), "shareData");
        if (!shareDir.exists()) {
          shareDir.mkdirs();
        }

        String fileName = UUID.randomUUID().toString() + ".png";
        File imageFile = new File(shareDir, fileName);

        NativeWechatUtils.downloadToFile(url, imageFile, new NativeWechatUtils.DownloadFileCallback() {
          @Override
          public void onFailure(@NonNull Call call, @NonNull IOException e) {
            callback.invoke(true, "Failed to download image: " + e.getMessage());
          }

          @Override
          public void onResponse(@NonNull File downloadedFile) {
            try {
              Uri contentUri = FileProvider.getUriForFile(
                      reactContext,
                      reactContext.getPackageName() + ".nativewechat.fileprovider",
                      downloadedFile
              );

              reactContext.grantUriPermission(
                      "com.tencent.mm",
                      contentUri,
                      Intent.FLAG_GRANT_READ_URI_PERMISSION
              );

              WXImageObject imgObj = new WXImageObject();
              imgObj.imagePath = contentUri.toString();

              WXMediaMessage msg = new WXMediaMessage();
              msg.mediaObject = imgObj;

              Bitmap thumbBitmap = NativeWechatUtils.loadBitmapFromFile(downloadedFile.getAbsolutePath(), 128);
              if (thumbBitmap != null) {
                msg.thumbData = NativeWechatUtils.bmpToByteArray(thumbBitmap, true);
                thumbBitmap.recycle();
              } else {
                callback.invoke(true, "Failed to create thumbnail from downloaded image.");
                return;
              }

              SendMessageToWX.Req req = new SendMessageToWX.Req();
              req.message = msg;
              req.scene = scene;

              if (!wxApi.sendReq(req)) {
                callback.invoke(true, "Failed to send FileProvider share request to WeChat.");
              } else {
                callback.invoke((Object) null);
              }

            } catch (IllegalArgumentException iae) {
               callback.invoke(true, "FileProvider URI generation failed: " + iae.getMessage() + ". Ensure 'shareData' is configured in file_provider_paths.xml.");
            } catch (Exception e) {
               callback.invoke(true, "FileProvider sharing failed: " + e.getMessage());
            }
          }
        });

      } catch (Exception e) {
        callback.invoke(true, "FileProvider setup failed: " + e.getMessage());
      }
    } else {
      NativeWechatUtils.downloadFileAsBitmap(url, new NativeWechatUtils.DownloadBitmapCallback() {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
          callback.invoke(true, "Download failed (fallback): " + e.getMessage());
        }

        @Override
        public void onResponse(@NonNull Bitmap bitmap) {
          WXImageObject imgObj = new WXImageObject(bitmap);

          WXMediaMessage msg = new WXMediaMessage();
          msg.mediaObject = imgObj;

          Bitmap thumbBitmap = NativeWechatUtils.compressImage(bitmap, 128);
          if (thumbBitmap != null) {
             msg.thumbData = NativeWechatUtils.bmpToByteArray(thumbBitmap, true);
          } else {
             msg.thumbData = null;
          }

          SendMessageToWX.Req req = new SendMessageToWX.Req();
          req.message = msg;
          req.scene = scene;

          if (!wxApi.sendReq(req)) {
             callback.invoke(true, "Failed to send fallback share request to WeChat.");
          } else {
             callback.invoke((Object) null);
          }
        }
      });
    }
  }

  public void shareVideo(ReadableMap request, Callback callback) {
    String videoUrl = request.getString("videoUrl");
    String videoLowBandUrl = request.getString("videoLowBandUrl");
    String title = request.getString("title");
    String description = request.getString("description");
    String coverUrl = request.getString("coverUrl");
    int scene = request.getInt("scene");

    WXVideoObject video = new WXVideoObject();
    video.videoUrl = videoUrl;

    if (videoLowBandUrl != null)
      video.videoLowBandUrl = videoLowBandUrl;

    WXMediaMessage msg = new WXMediaMessage(video);
    msg.title = title;
    msg.description = description;

    BitmapDownload onCoverDownloaded = (bitmap) -> {
      if (bitmap != null) {
        msg.thumbData = NativeWechatUtils.bmpToByteArray(NativeWechatUtils.compressImage(bitmap, 128),
          true);
      }

      SendMessageToWX.Req req = new SendMessageToWX.Req();
      req.message = msg;
      req.scene = scene;

      callback.invoke(wxApi.sendReq(req) ? null : true);
    };

    if (coverUrl != null && coverUrl.length() > 0) {
      NativeWechatUtils.downloadFileAsBitmap(coverUrl, new NativeWechatUtils.DownloadBitmapCallback() {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
          callback.invoke(true, e.getMessage());
        }

        @Override
        public void onResponse(@NonNull Bitmap bitmap) {
          onCoverDownloaded.run(bitmap);
        }
      });
    } else {
      onCoverDownloaded.run(null);
    }
  }

  public void shareWebpage(ReadableMap request, Callback callback) {
    String webpageUrl = request.getString("webpageUrl");
    String title = request.getString("title");
    String description = request.getString("description");
    String coverUrl = request.getString("coverUrl");
    int scene = request.getInt("scene");

    WXWebpageObject webpageObj = new WXWebpageObject();
    webpageObj.webpageUrl = webpageUrl;

    WXMediaMessage msg = new WXMediaMessage(webpageObj);
    msg.title = title;
    msg.description = description;

    BitmapDownload onCoverDownloaded = (bitmap) -> {
      if (bitmap != null) {
        msg.thumbData = NativeWechatUtils.bmpToByteArray(NativeWechatUtils.compressImage(bitmap, 128),
          true);
      }

      SendMessageToWX.Req req = new SendMessageToWX.Req();
      req.message = msg;
      req.scene = scene;

      callback.invoke(wxApi.sendReq(req) ? null : true);
    };

    if (coverUrl != null && coverUrl.length() > 0) {
      NativeWechatUtils.downloadFileAsBitmap(coverUrl, new NativeWechatUtils.DownloadBitmapCallback() {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
          callback.invoke(true, e.getMessage());
        }

        @Override
        public void onResponse(@NonNull Bitmap bitmap) {
          onCoverDownloaded.run(bitmap);
        }
      });
    } else {
      onCoverDownloaded.run(null);
    }
  }

  public void shareMiniProgram(ReadableMap request, Callback callback) {
    String webpageUrl = request.getString("webpageUrl");
    String userName = request.getString("userName");
    String path = request.getString("path");
    String title = request.getString("title");
    String description = request.getString("description");
    String coverUrl = request.getString("coverUrl");
    boolean withShareTicket = request.getBoolean("withShareTicket");
    int miniprogramType = request.getInt("miniprogramType");
    int scene = request.getInt("scene");

    WXMiniProgramObject miniProgramObj = new WXMiniProgramObject();
    miniProgramObj.webpageUrl = webpageUrl;
    miniProgramObj.miniprogramType = miniprogramType;
    miniProgramObj.userName = userName;
    miniProgramObj.path = path;
    miniProgramObj.withShareTicket = withShareTicket;

    WXMediaMessage msg = new WXMediaMessage(miniProgramObj);
    msg.title = title;
    msg.description = description;

    BitmapDownload onCoverDownloaded = (bitmap) -> {
      if (bitmap != null) {
        msg.thumbData = NativeWechatUtils.bmpToByteArray(NativeWechatUtils.compressImage(bitmap, 128),
          true);
      }

      SendMessageToWX.Req req = new SendMessageToWX.Req();
      req.message = msg;
      req.scene = scene;

      callback.invoke(wxApi.sendReq(req) ? null : true);
    };

    if (coverUrl != null && coverUrl.length() > 0) {
      NativeWechatUtils.downloadFileAsBitmap(coverUrl, new NativeWechatUtils.DownloadBitmapCallback() {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
          callback.invoke(true, e.getMessage());
        }

        @Override
        public void onResponse(@NonNull Bitmap bitmap) {
          onCoverDownloaded.run(bitmap);
        }
      });
    } else {
      onCoverDownloaded.run(null);
    }
  }

  public void requestPayment(ReadableMap request, Callback callback) {
    PayReq payReq = new PayReq();

    payReq.partnerId = request.getString("partnerId");
    payReq.prepayId = request.getString("prepayId");
    payReq.nonceStr = request.getString("nonceStr");
    payReq.timeStamp = request.getString("timeStamp");
    payReq.sign = request.getString("sign");
    payReq.packageValue = "Sign=WXPay";
    payReq.extData = request.getString("extData");
    payReq.appId = appid;

    callback.invoke(wxApi.sendReq(payReq) ? null : true);
  }

  public void requestSubscribeMessage(ReadableMap request, Callback callback) {
    String templateId = request.getString("templateId");
    String reserved = request.getString("reserved");
    int scene = request.getInt("int");

    SubscribeMessage.Req req = new SubscribeMessage.Req();
    req.scene = scene;
    req.templateID = templateId;
    req.reserved = reserved;

    callback.invoke(wxApi.sendReq(req) ? null : true);
  }

  public void launchMiniProgram(ReadableMap request, Callback callback) {
    String userName = request.getString("userName");
    String path = request.getString("path");
    int miniprogramType = request.getInt("miniprogramType");

    WXLaunchMiniProgram.Req req = new WXLaunchMiniProgram.Req();
    req.userName = userName;
    req.path = path;
    req.miniprogramType = miniprogramType;

    callback.invoke(wxApi.sendReq(req) ? null : true);
  }

  public void openCustomerService(ReadableMap request, Callback callback) {
    String corpId = request.getString("corpid");
    String url = request.getString("url");

    WXOpenCustomerServiceChat.Req req = new WXOpenCustomerServiceChat.Req();
    req.corpId = corpId;
    req.url = url;

    callback.invoke(wxApi.sendReq(req) ? null : true);
  }

  @Override
  public void onReq(BaseReq req) {
  }

  @Override
  public void onResp(BaseResp baseResp) {
    WritableMap convertedData = NativeWechatRespDataHelper.downcastResp(baseResp);

    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit("NativeWechat_Response", convertedData);
  }

  interface BitmapDownload {
    void run(@Nullable Bitmap str);
  }

  private boolean checkVersionValid() {
    if (wxApi == null) return false;
    return wxApi.getWXAppSupportAPI() >= 0x27000D00;
  }

  private boolean checkAndroidNotBelowN() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
  }

}
