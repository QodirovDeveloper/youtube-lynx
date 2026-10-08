#import "TemplateProvider.h"

@implementation TemplateProvider

+ (NSData *)mainBundleData {
  NSString *path = [[NSBundle mainBundle] pathForResource:@"main" ofType:@"lynxbundle"];
  return path ? [NSData dataWithContentsOfFile:path] : nil;
}

- (void)loadTemplateWithUrl:(NSString *)url onComplete:(LynxTemplateLoadBlock)callback {
  NSData *data = [TemplateProvider mainBundleData];
  if (data) {
    callback(data, nil);
  } else {
    callback(nil, [NSError errorWithDomain:@"YtLynx"
                                      code:404
                                  userInfo:@{NSLocalizedDescriptionKey : @"main.lynxbundle topilmadi"}]);
  }
}

@end
