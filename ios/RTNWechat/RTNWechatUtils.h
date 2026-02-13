//
//  RTNWechatUtils.h
//  Pods
//
//  Created by Hector Chong on 10/21/22.
//

#ifndef RTNWechatUtils_h
#define RTNWechatUtils_h

@interface RTNWechatUtils : NSObject

+ (void)downloadFile:(NSURL *)url onSuccess:(void (^)(NSData * _Nullable data))onSuccess onError:(void (^)(NSError *error))onError;


+ (NSData *_Nonnull)compressImage:(NSData *_Nonnull)data toByte:(NSUInteger)maxLength;

@end


#endif /* RTNWechatUtils_h */
