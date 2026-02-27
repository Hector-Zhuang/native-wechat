//
//  RTNWechatRespDataHelper.h
//  RTNWechatRespDataHelper
//
//  Created by Hector Chong on 9/18/22.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import "WechatOpenSDK/WXApi.h"

@interface RTNWechatRespDataHelper: NSObject

+ (NSDictionary *)downcastResp: (BaseResp *)baseResp;

@end
