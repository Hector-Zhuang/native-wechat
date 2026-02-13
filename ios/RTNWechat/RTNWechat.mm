#import "RTNWechat.h"
#import "WXApiObject.h"
#import "RTNWechatUtils.h"
#import "RTNWechatRespDataHelper.h"

#ifdef RCT_NEW_ARCH_ENABLED
#import "NativeWechatSpec.h"
#endif

@implementation RTNWechat
{
  bool hasListeners;
}

@synthesize appid = _appid;

- (instancetype)init
{
    if(self = [super init]){
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleOpenURL:) name:@"RCTOpenURLNotification" object:nil];
    }
          
    return self;
}

- (void)dealloc
{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (BOOL)handleOpenURL: (NSNotification *)notification
{
    NSDictionary* userInfo = notification.userInfo;
        
    return [WXApi handleOpenURL:[NSURL URLWithString: userInfo[@"url"]] delegate:self];
}

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

RCT_EXPORT_MODULE(Wechat)

RCT_EXPORT_METHOD(registerApp:
                  (NSDictionary *)params
                  )
{
    BOOL log = [params valueForKey:@"log"];
    NSString *appid = [params valueForKey:@"appid"];
    NSString *logPrefix = [params valueForKey:@"logPrefix"];
    NSString *universalLink = [params valueForKey:@"universalLink"];

    if(log){
        [WXApi startLogByLevel:WXLogLevelDetail logBlock:^(NSString *log) {
            NSLog([NSString stringWithFormat:@"%@%@: ", logPrefix, @" %@"], log);
        }];
    }
    
    self.appid = appid;
        
    [WXApi registerApp:appid universalLink:universalLink];
}

RCT_EXPORT_METHOD(checkUniversalLinkReady:
                 (RCTResponseSenderBlock)callback
                 )
{
    __block BOOL success = YES;
    
    [WXApi checkUniversalLinkReady:^(WXULCheckStep step, WXCheckULStepResult * _Nonnull result) {
        if(result.success){
            if(step == WXULCheckStepFinal){
                callback(@[@NO, @{
                    @"suggestion": @"",
                    @"errorInfo": @"",
                }]);
            }
        }else{
            if(success){
                success = NO;

                callback(@[@YES, @{
                    @"suggestion": result.suggestion,
                    @"errorInfo": result.errorInfo
                    }
                ]);
            }
        }
    }];
}

RCT_EXPORT_METHOD(isWechatInstalled:
                  (RCTResponseSenderBlock)callback
                  )
{
    BOOL installed = [WXApi isWXAppInstalled];
    callback(@[[NSNull null], @(installed)]);
}

RCT_EXPORT_METHOD(sendAuthRequest:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                 )
{
    SendAuthReq* req = [[SendAuthReq alloc] init];

    req.scope = [params valueForKey:@"scope"];
    req.state = [params valueForKey:@"state"];
    
    [WXApi sendReq:req completion:^(BOOL success){
        callback(@[[NSNumber numberWithBool:!success]]);
    }];}

RCT_EXPORT_METHOD(shareText:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];

    req.bText = YES;
    req.text = [params valueForKey:@"text"];
    req.scene = [[params valueForKey:@"scene"] unsignedIntValue];
    
    [WXApi sendReq:req completion:^(BOOL success){
        callback(@[[NSNumber numberWithBool:!success]]);
    }];
}

RCT_EXPORT_METHOD(shareImage:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    NSURL *url = [NSURL URLWithString:[params valueForKey:@"src"]];
        
    [RTNWechatUtils downloadFile:url onSuccess:^(NSData * _Nullable data) {
        WXImageObject *imageObject = [WXImageObject object];
        imageObject.imageData = data;
        
        WXMediaMessage *message = [WXMediaMessage message];
        message.thumbData = [RTNWechatUtils compressImage:data toByte:32768];;
        message.mediaObject = imageObject;
        
        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];

        req.bText = NO;
        req.scene = [[params valueForKey:@"scene"] unsignedIntValue];
        req.message = message;
        
        [WXApi sendReq:req completion:^(BOOL success){
            callback(@[[NSNumber numberWithBool:!success]]);
        }];
    } onError:^(NSError *error) {
        callback(@[@1, error.localizedDescription]);
    }];
}

RCT_EXPORT_METHOD(shareVideo:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    WXVideoObject *videoObj = [WXVideoObject object];
    videoObj.videoUrl = params[@"videoUrl"];
    videoObj.videoLowBandUrl = params[@"videoLowBandUrl"];
    
    WXMediaMessage *message = [WXMediaMessage message];
    message.title = params[@"title"];
    message.description = params[@"description"];
    message.mediaObject = videoObj;
    
    void (^onCoverDownloaded)(NSData * _Nullable data) = ^void(NSData * _Nullable data){
        if(data){
            [message setThumbData:data];
        }
        
        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.message = message;
        req.scene = [params[@"scene"] unsignedIntValue];
        
        [WXApi sendReq:req completion:^(BOOL success){
            callback(@[[NSNumber numberWithBool:!success]]);
        }];
    };
    
    if(params[@"coverUrl"] != nil && [params[@"coverUrl"] length]){
        NSURL *url = [NSURL URLWithString:params[@"coverUrl"]];
        
        [RTNWechatUtils downloadFile:url onSuccess:^(NSData * _Nullable data) {
            onCoverDownloaded(data);
        } onError:^(NSError *error) {
            callback(@[@1, error.localizedDescription]);
        }];
    }else{
        onCoverDownloaded(nil);
    }
}

RCT_EXPORT_METHOD(shareWebpage:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    WXWebpageObject *webpageObj = [WXWebpageObject object];
    webpageObj.webpageUrl = params[@"webpageUrl"];
    
    WXMediaMessage *message = [WXMediaMessage message];
    message.title = params[@"title"];
    message.description = params[@"description"];;
    message.mediaObject = webpageObj;
    
    void (^onCoverDownloaded)(NSData * _Nullable data) = ^void(NSData * _Nullable data){
        if(data){
            [message setThumbData:data];
        }
        
        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.message = message;
        req.scene = [params[@"scene"] unsignedIntValue];

        [WXApi sendReq:req completion:^(BOOL success){
            callback(@[[NSNumber numberWithBool:!success]]);
        }];
    };
        
    if(params[@"coverUrl"] != nil && [params[@"coverUrl"] length]){
        NSURL *url = [NSURL URLWithString:params[@"coverUrl"]];
        
        [RTNWechatUtils downloadFile:url onSuccess:^(NSData * _Nullable data) {
            onCoverDownloaded(data);
        } onError:^(NSError *error) {
            callback(@[@1, error.localizedDescription]);
        }];
    }else{
        onCoverDownloaded(nil);
    }
}

RCT_EXPORT_METHOD(shareMiniProgram:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    WXMiniProgramObject *object = [WXMiniProgramObject object];
    object.webpageUrl = params[@"webpageUrl"];
    object.userName = params[@"userName"];
    object.path = params[@"path"];
    object.withShareTicket = params[@"withShareTicket"];
    object.miniProgramType = (WXMiniProgramType) [params[@"miniprogramType"] unsignedIntValue];
    
    WXMediaMessage *message = [WXMediaMessage message];
    message.title = params[@"title"];
    message.description = params[@"description"];
    message.mediaObject = object;
    
    void (^onCoverDownloaded)(NSData * _Nullable data) = ^void(NSData * _Nullable data){
        if(data){
            NSInteger size = [data length] / 1024;
            
            if(size > 128){
                return callback(@[@1, [NSString stringWithFormat:@"The maximum size of cover image is 128kb while the passing one is %zd%% kb", size]]);
            }
            
            [message setThumbData:data];
            [object setHdImageData:data];
        }
        
        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.scene = [params[@"scene"] unsignedIntValue];
        req.message = message;
        
        [WXApi sendReq:req completion:^(BOOL success){            
            callback(@[[NSNumber numberWithBool:!success]]);
        }];
    };
    
    if(params[@"coverUrl"] != nil && [params[@"coverUrl"] length]){
        NSURL *url = [NSURL URLWithString:params[@"coverUrl"]];
        
        [RTNWechatUtils downloadFile:url onSuccess:^(NSData * _Nullable data) {
            onCoverDownloaded(data);
        } onError:^(NSError *error) {
            callback(@[@1, error.localizedDescription]);
        }];
    }else{
        onCoverDownloaded(nil);
    }
}

RCT_EXPORT_METHOD(requestPayment:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    PayReq *request = [[PayReq alloc] init];
    
    request.partnerId = [params valueForKey:@"partnerId"];
    request.prepayId = [params valueForKey:@"prepayId"];
    request.package = @"Sign=WXPay";
    request.nonceStr = [params valueForKey:@"nonceStr"];
    request.timeStamp = UInt32([[params valueForKey:@"timeStamp"] unsignedIntValue]);
    request.sign = [params valueForKey:@"sign"];
    
    [WXApi sendReq:request completion:^(BOOL success){
        callback(@[[NSNumber numberWithBool:!success]]);
    }];
}

RCT_EXPORT_METHOD(requestSubscribeMessage:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    WXSubscribeMsgReq *req = [[WXSubscribeMsgReq alloc] init];
    
    req.scene = [[params valueForKey:@"scene"] unsignedIntValue];
    req.templateId = [params valueForKey:@"templateId"];
    req.reserved = [params valueForKey:@"reserved"];

    [WXApi sendReq:req completion:^(BOOL success){
        callback(@[[NSNumber numberWithBool:!success]]);
    }];
}

RCT_EXPORT_METHOD(launchMiniProgram:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    WXLaunchMiniProgramReq *req = [WXLaunchMiniProgramReq object];
    
    req.userName = [params valueForKey:@"userName"];
    req.path = [params valueForKey:@"path"];
    req.miniProgramType = (WXMiniProgramType)[[params valueForKey:@"miniprogramType"] unsignedIntValue];
    
    [WXApi sendReq:req completion:^(BOOL success){
        callback(@[[NSNumber numberWithBool:!success]]);
    }];
}

RCT_EXPORT_METHOD(openCustomerService:
                  (NSDictionary *)params
                  callback:(RCTResponseSenderBlock)callback
                  )
{
    WXOpenCustomerServiceReq *req = [[WXOpenCustomerServiceReq alloc] init];
    
    req.corpid = [params valueForKey:@"corpid"];
    req.url = [params valueForKey:@"url"];
    
    [WXApi sendReq:req completion:^(BOOL success){
        callback(@[[NSNumber numberWithBool:!success]]);
    }];
}

- (NSDictionary *)constantsToExport
{
 return @{
     @"WXSceneSession": [NSNumber numberWithInt:WXSceneSession],
     @"WXSceneTimeline": [NSNumber numberWithInt:WXSceneTimeline],
     @"WXSceneFavorite": [NSNumber numberWithInt:WXSceneFavorite],
     @"WXMiniProgramTypeRelease": [NSNumber numberWithInt:WXMiniProgramTypeRelease],
     @"WXMiniProgramTypeTest": [NSNumber numberWithInt:WXMiniProgramTypeTest],
     @"WXMiniProgramTypePreview": [NSNumber numberWithInt:WXMiniProgramTypePreview]
 };
}

- (NSArray<NSString *> *)supportedEvents
{
  return @[@"NativeWechat_Response"];
}

- (void)onResp:(BaseResp *)baseResp{
    NSDictionary* convertedData = [RTNWechatRespDataHelper downcastResp:baseResp];
    
    [self sendEventWithName:@"NativeWechat_Response" body:convertedData];
}

- (void)startObserving
{
    hasListeners = YES;
}

- (void)stopObserving
{
    hasListeners = NO;
}

#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeWechatSpecJSI>(params);
}
#endif

@end
