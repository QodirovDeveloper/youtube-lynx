#import <Foundation/Foundation.h>
#import <Lynx/LynxTemplateProvider.h>

NS_ASSUME_NONNULL_BEGIN

// Lynx bundle'ni ilova ichidan o'qiydi (Resources/main.lynxbundle — CI nusxalaydi)
@interface TemplateProvider : NSObject <LynxTemplateProvider>
+ (nullable NSData *)mainBundleData;
@end

NS_ASSUME_NONNULL_END
