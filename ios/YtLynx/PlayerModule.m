#import "PlayerModule.h"

#import "Player.h"

// Lynx bu metodlarni o'z JS thread'ida chaqiradi, Player esa faqat main thread'da ishlaydi
@implementation PlayerModule

+ (NSString *)name {
  return @"PlayerModule";
}

+ (NSDictionary<NSString *, NSString *> *)methodLookup {
  return @{
    @"command" : NSStringFromSelector(@selector(command:value:)),
    @"load" : NSStringFromSelector(@selector(load:)),
  };
}

// play, pause, toggle, next, prev, skip (+/- soniya), sleep (daqiqa), back
- (void)command:(NSString *)action value:(NSNumber *)value {
  if (![action isKindOfClass:NSString.class]) return;
  double v = [value isKindOfClass:NSNumber.class] ? value.doubleValue : 0;
  dispatch_async(dispatch_get_main_queue(), ^{
    [[Player shared] command:action value:v];
  });
}

- (void)load:(NSString *)url {
  if (![url isKindOfClass:NSString.class] || ![url hasPrefix:@"https://"]) return;
  dispatch_async(dispatch_get_main_queue(), ^{
    [[Player shared] load:url];
  });
}

@end
