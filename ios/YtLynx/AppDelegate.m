#import "AppDelegate.h"

#import <AVFoundation/AVFoundation.h>
#import <Lynx/LynxConfig.h>
#import <Lynx/LynxEnv.h>

#import "Player.h"
#import "TemplateProvider.h"
#import "ViewController.h"

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary<UIApplicationLaunchOptionsKey, id> *)launchOptions {
  // Fonda ijro: "playback" toifasi + Info.plist'dagi UIBackgroundModes=audio.
  // Shunda ekran qulflanganda yoki boshqa ilovaga o'tganda ham ovoz davom etadi.
  NSError *error = nil;
  [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:&error];
  [[AVAudioSession sharedInstance] setActive:YES error:&error];
  if (error) NSLog(@"[YtLynx] AVAudioSession: %@", error);

  // Lynx'ni ishga tushirish (har qanday LynxView'dan oldin)
  [[LynxEnv sharedInstance]
      prepareConfig:[[LynxConfig alloc] initWithProvider:[[TemplateProvider alloc] init]]];

  self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
  self.window.rootViewController = [[ViewController alloc] init];
  [self.window makeKeyAndVisible];
  return YES;
}

// ytlynx://open?url=https%3A%2F%2Fyoutu.be%2F... — masalan, iOS "Qisqa buyruqlar" (Shortcuts)
// orqali "Ulashish → YT Lynx" qilish uchun
- (BOOL)application:(UIApplication *)app
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey, id> *)options {
  NSURLComponents *components = [NSURLComponents componentsWithURL:url resolvingAgainstBaseURL:NO];
  NSString *link = nil;
  for (NSURLQueryItem *item in components.queryItems) {
    if ([item.name isEqualToString:@"url"]) link = item.value;
  }
  NSString *target = [Player youtubeUrlFromText:link];
  if (!target) return NO;
  [[Player shared] open:target];
  return YES;
}

@end
