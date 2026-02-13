//
//  RTNWechatUtils.m
//  CocoaAsyncSocket
//
//  Created by Hector Chong on 10/21/22.
//

#import <Foundation/Foundation.h>
#import "RTNWechatUtils.h"

@implementation RTNWechatUtils

+ (void)downloadFile:(NSURL *)url onSuccess:(void (^)(NSData * _Nullable data))onSuccess onError:(void (^)(NSError *error))onError
{
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    
    NSURLSessionConfiguration *configuration = [NSURLSessionConfiguration defaultSessionConfiguration];
    NSURLSession *session = [NSURLSession sessionWithConfiguration:configuration];
    
    NSURLSessionDataTask *task = [session dataTaskWithRequest: request completionHandler:^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
      if(error){
          onError(error);
      }else{
          onSuccess(data);
      }
    }];
    
    [task resume];
}

+ (NSData *)compressImage:(NSData *)data toByte:(NSUInteger)maxLength {
    
    if (data.length < maxLength) return data;
    
    UIImage *image = [UIImage imageWithData:data];
    // Compress by quality
    CGFloat compression = 1;
    CGFloat max = 1;
    CGFloat min = 0;
    for (int i = 0; i < 6; ++i) {
        compression = (max + min) / 2;
        data = UIImageJPEGRepresentation(image, compression);
        if (data.length < maxLength * 0.9) {
            min = compression;
        } else if (data.length > maxLength) {
            max = compression;
        } else {
            break;
        }
    }
    
    if (data.length < maxLength) return data;
    
    // Compress by size
    NSUInteger lastDataLength = 0;
    while (data.length > maxLength && data.length != lastDataLength) {
        lastDataLength = data.length;
        CGFloat ratio = (CGFloat)maxLength / data.length;
        CGSize size = CGSizeMake((NSUInteger)(image.size.width * sqrtf(ratio)),
                                 (NSUInteger)(image.size.height * sqrtf(ratio))); // Use NSUInteger to prevent white blank
        UIGraphicsBeginImageContext(size);
        [image drawInRect:CGRectMake(0, 0, size.width, size.height)];
        image = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
        data = UIImageJPEGRepresentation(image, compression);
    }
    
    return data;
}

@end
