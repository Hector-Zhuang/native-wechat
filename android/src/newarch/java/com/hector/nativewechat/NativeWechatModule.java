package com.hector.nativewechat;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import java.util.Map;

public class NativeWechatModule extends NativeWechatSpec {
  private NativeWechatModuleImpl moduleImpl;

  NativeWechatModule(ReactApplicationContext context) {
    super(context);

    moduleImpl = new NativeWechatModuleImpl(context);
  }

  @Override
  public String getName() {
    return NativeWechatModuleImpl.NAME;
  }

  @Override
  public Map<String, Object> getConstants() {
    return NativeWechatModuleImpl.getConstants();
  }

  @ReactMethod
  public void sendAuthRequest(ReadableMap request, Callback callback) {
    moduleImpl.sendAuthRequest(request, callback);
  }

  @ReactMethod
  public void registerApp(ReadableMap request) {
    moduleImpl.registerApp(request);
  }

  @ReactMethod
  public void shareText(ReadableMap request, Callback callback) {
    moduleImpl.shareText(request, callback);
  }

  @ReactMethod
  public void shareImage(ReadableMap request, Callback callback) {
    moduleImpl.shareImage(request, callback);
  }

  @ReactMethod
  public void shareVideo(ReadableMap request, Callback callback) {
    moduleImpl.shareVideo(request, callback);
  }

  @ReactMethod
  public void shareWebpage(ReadableMap request, Callback callback) {
    moduleImpl.shareWebpage(request, callback);
  }

  @ReactMethod
  public void shareMiniProgram(ReadableMap request, Callback callback) {
    moduleImpl.shareMiniProgram(request, callback);
  }

  @ReactMethod
  public void isWechatInstalled(Callback callback) {
    moduleImpl.isWechatInstalled(callback);
  }

  @ReactMethod
  public void requestPayment(ReadableMap request, Callback callback) {
    moduleImpl.requestPayment(request, callback);
  }

  @ReactMethod
  public void requestSubscribeMessage(ReadableMap request, Callback callback) {
    moduleImpl.requestSubscribeMessage(request, callback);
  }

  @ReactMethod
  public void launchMiniProgram(ReadableMap request, Callback callback) {
    moduleImpl.launchMiniProgram(request, callback);
  }

  @ReactMethod
  public void openCustomerService(ReadableMap request, Callback callback) {
    moduleImpl.openCustomerService(request, callback);
  }

  @ReactMethod
  public void addListener(String eventName) {
  }

  @ReactMethod
  public void removeListeners(Integer count) {
  }
}
