#import "StorageModule.h"

static NSString *const kSuite = @"YtLynxStorage";

@implementation StorageModule {
  NSUserDefaults *_storage;
}

- (instancetype)init {
  if (self = [super init]) {
    _storage = [[NSUserDefaults alloc] initWithSuiteName:kSuite];
  }
  return self;
}

+ (NSString *)name {
  return @"StorageModule";
}

+ (NSDictionary<NSString *, NSString *> *)methodLookup {
  return @{
    @"setItem" : NSStringFromSelector(@selector(setItem:value:)),
    @"getItem" : NSStringFromSelector(@selector(getItem:callback:)),
  };
}

- (void)setItem:(NSString *)key value:(NSString *)value {
  [_storage setObject:value forKey:key];
}

- (void)getItem:(NSString *)key callback:(void (^)(NSString *value))callback {
  callback([_storage stringForKey:key]);
}

@end
