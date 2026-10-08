#import "Player.h"

#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MediaPlayer.h>
#import <QuartzCore/QuartzCore.h>

@implementation Player {
  __weak WKWebView *_web;
  __weak id<PlayerListener> _listener;
  NSString *_pendingUrl;

  // uxlash taymeri
  NSDate *_sleepAt;
  NSTimer *_sleepTimer;

  // ekran qulfi uchun muqova
  UIImage *_art;

  // ekran qulfiga oxirgi marta nima yuborilgan (har soniya yangilamaslik uchun)
  NSString *_nowPlayingKey;
  double _nowPlayingPos;
  BOOL _nowPlayingPlaying;
  CFTimeInterval _nowPlayingAt;
  BOOL _remoteReady;

  // qo'ng'iroq tugagach davom ettirish kerakmi
  BOOL _resumeAfterInterruption;
}

+ (instancetype)shared {
  static Player *instance;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    instance = [[Player alloc] init];
  });
  return instance;
}

- (instancetype)init {
  if (self = [super init]) {
    [self resetState];
    NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
    // Ilova fonga o'tganda (ekran qulflanganda ham) iOS videoni pauza qiladi — qayta boshlaymiz
    [center addObserver:self
               selector:@selector(appWentToBackground)
                   name:UIApplicationWillResignActiveNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(appWentToBackground)
                   name:UIApplicationDidEnterBackgroundNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(audioInterrupted:)
                   name:AVAudioSessionInterruptionNotification
                 object:nil];
  }
  return self;
}

- (void)resetState {
  _url = @"";
  _videoId = @"";
  _title = @"";
  _channel = @"";
  _canGoBack = NO;
  _playing = NO;
  _position = 0;
  _duration = 0;
  _art = nil;
  _nowPlayingKey = @"";
}

#pragma mark - ulanish

- (void)attachWebView:(WKWebView *)webView listener:(id<PlayerListener>)listener {
  _web = webView;
  _listener = listener;
}

- (void)detachWebView:(WKWebView *)webView {
  if (_web != webView && _web != nil) return;
  _web = nil;
  _listener = nil;
  [self setSleep:0];
  [self resetState];
  MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo = nil;
}

#pragma mark - sahifadan keladigan holat

static NSString *Str(id value) {
  return [value isKindOfClass:NSString.class] ? value : @"";
}

static double Num(id value) {
  if (![value isKindOfClass:NSNumber.class]) return 0;
  double v = [value doubleValue];
  return (isnan(v) || isinf(v) || v < 0) ? 0 : v;
}

- (void)onPage:(NSString *)json {
  NSData *data = [json dataUsingEncoding:NSUTF8StringEncoding];
  id parsed = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
  if (![parsed isKindOfClass:NSDictionary.class]) return;
  NSDictionary *m = parsed;

  NSString *videoId = Str(m[@"id"]);
  if (![videoId isEqualToString:_videoId]) {
    _art = nil;
    if (videoId.length > 0) [self loadArt:videoId];
  }
  _videoId = videoId;
  _title = Str(m[@"title"]);
  _channel = Str(m[@"channel"]);
  _playing = [m[@"playing"] isKindOfClass:NSNumber.class] && [m[@"playing"] boolValue];
  _position = Num(m[@"pos"]);
  _duration = Num(m[@"dur"]);
  [self checkSleep];
  [self publish];
}

- (void)onNavigation:(NSString *)url canGoBack:(BOOL)canGoBack {
  _url = url ?: @"";
  _canGoBack = canGoBack;
  [_listener playerDidChange];
}

#pragma mark - buyruqlar

- (void)command:(NSString *)action value:(double)value {
  if ([action isEqualToString:@"sleep"]) {
    [self setSleep:value];
    [_listener playerDidChange];
    return;
  }
  if ([action isEqualToString:@"back"]) {
    if (_web.canGoBack) [_web goBack];
    return;
  }
  [self runInPage:action value:value];
}

// Java → JS o'rniga: window.__ytCmd('next', 0)
- (void)runInPage:(NSString *)action value:(double)value {
  static NSCharacterSet *notLetters;
  static dispatch_once_t once;
  dispatch_once(&once, ^{
    notLetters = [[NSCharacterSet lowercaseLetterCharacterSet] invertedSet];
  });
  if (action.length == 0 || [action rangeOfCharacterFromSet:notLetters].location != NSNotFound) return;
  NSString *script =
      [NSString stringWithFormat:@"window.__ytCmd&&window.__ytCmd('%@',%f)", action, value];
  [_web evaluateJavaScript:script completionHandler:nil];
}

- (void)load:(NSString *)url {
  NSURL *u = [NSURL URLWithString:url];
  if (u && _web) [_web loadRequest:[NSURLRequest requestWithURL:u]];
}

- (void)open:(NSString *)url {
  if (_web) {
    [self load:url];
  } else {
    _pendingUrl = url;
  }
}

- (NSString *)takePendingUrl:(NSString *)fallback {
  NSString *result = _pendingUrl ?: fallback;
  _pendingUrl = nil;
  return result;
}

#pragma mark - fonda ijro

- (void)appWentToBackground {
  if (!_web) return;
  // iOS videoni bir zumda emas, biroz keyin pauza qilishi mumkin — bir necha marta urinamiz.
  // yt-bridge.js faqat video hozirgina o'ynayotgan va pauzani foydalanuvchi bosmagan bo'lsa davom ettiradi.
  for (NSNumber *delay in @[ @0.4, @1.2, @2.5, @4.0 ]) {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delay.doubleValue * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     [self runInPage:@"resume" value:0];
                   });
  }
}

- (void)audioInterrupted:(NSNotification *)note {
  NSUInteger type = [note.userInfo[AVAudioSessionInterruptionTypeKey] unsignedIntegerValue];
  if (type == AVAudioSessionInterruptionTypeBegan) {
    // qo'ng'iroq yoki boshqa ilova ovozi
    _resumeAfterInterruption = _playing;
    return;
  }
  NSUInteger options = [note.userInfo[AVAudioSessionInterruptionOptionKey] unsignedIntegerValue];
  if (_resumeAfterInterruption && (options & AVAudioSessionInterruptionOptionShouldResume)) {
    [[AVAudioSession sharedInstance] setActive:YES error:nil];
    [self runInPage:@"play" value:0];
  }
  _resumeAfterInterruption = NO;
}

#pragma mark - uxlash taymeri

- (void)setSleep:(double)minutes {
  [_sleepTimer invalidate];
  _sleepTimer = nil;
  _sleepAt = minutes > 0 ? [NSDate dateWithTimeIntervalSinceNow:minutes * 60] : nil;
  if (_sleepAt) {
    _sleepTimer = [NSTimer scheduledTimerWithTimeInterval:minutes * 60
                                                   target:self
                                                 selector:@selector(checkSleep)
                                                 userInfo:nil
                                                  repeats:NO];
  }
}

// Taymer fonda kechikishi mumkin, shuning uchun har holat kelganda ham tekshiramiz
- (void)checkSleep {
  if (!_sleepAt || _sleepAt.timeIntervalSinceNow > 0) return;
  [self setSleep:0];
  NSLog(@"[YtLynx] uxlash taymeri: pauza");
  [self runInPage:@"pause" value:0];
  [_listener playerDidChange];
}

- (NSInteger)sleepLeftSeconds {
  return _sleepAt ? MAX(0, (NSInteger)ceil(_sleepAt.timeIntervalSinceNow)) : 0;
}

#pragma mark - tarqatish

- (NSDictionary<NSString *, id> *)stateDictionary {
  return @{
    @"url" : _url,
    @"canGoBack" : @(_canGoBack),
    @"videoId" : _videoId,
    @"title" : _title,
    @"channel" : _channel,
    @"playing" : @(_playing),
    @"position" : @(_position),
    @"duration" : @(_duration),
    @"sleepLeft" : @([self sleepLeftSeconds]),
  };
}

- (void)publish {
  // 1) Lynx UI — har safar (progress chizig'i uchun)
  [_listener playerDidChange];

  // 2) ekran qulfi — faqat muhim o'zgarishda; pozitsiyani tizim o'zi hisoblab boradi
  NSString *key = [NSString stringWithFormat:@"%@|%@|%@|%ld|%d|%d", _videoId, _title, _channel,
                                             (long)_duration, _playing, _art != nil];
  double expected = _nowPlayingPos;
  if (_nowPlayingPlaying) expected += CACurrentMediaTime() - _nowPlayingAt;
  BOOL jumped = fabs(_position - expected) > 2;
  if ([key isEqualToString:_nowPlayingKey] && !jumped) return;
  _nowPlayingKey = key;
  _nowPlayingPos = _position;
  _nowPlayingPlaying = _playing;
  _nowPlayingAt = CACurrentMediaTime();

  if (_videoId.length == 0) return;
  if (_playing) NSLog(@"[YtLynx] ijro: %@", _title);
  [self setupRemoteCommands];

  NSMutableDictionary *info = [NSMutableDictionary dictionary];
  info[MPMediaItemPropertyTitle] = _title.length > 0 ? _title : @"YouTube";
  info[MPMediaItemPropertyArtist] = _channel;
  if (_duration > 0) info[MPMediaItemPropertyPlaybackDuration] = @(_duration);
  info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(_position);
  info[MPNowPlayingInfoPropertyPlaybackRate] = @(_playing ? 1.0 : 0.0);
  if (_art) {
    UIImage *image = _art;
    info[MPMediaItemPropertyArtwork] =
        [[MPMediaItemArtwork alloc] initWithBoundsSize:image.size
                                        requestHandler:^UIImage *(CGSize size) {
                                          return image;
                                        }];
  }
  MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo = info;
}

// Ekran qulfi va naushnik tugmalari
- (void)setupRemoteCommands {
  if (_remoteReady) return;
  _remoteReady = YES;
  MPRemoteCommandCenter *center = MPRemoteCommandCenter.sharedCommandCenter;
  __weak Player *weakSelf = self;
  NSDictionary<NSString *, MPRemoteCommand *> *simple = @{
    @"play" : center.playCommand,
    @"pause" : center.pauseCommand,
    @"toggle" : center.togglePlayPauseCommand,
    @"next" : center.nextTrackCommand,
    @"prev" : center.previousTrackCommand,
  };
  [simple enumerateKeysAndObjectsUsingBlock:^(NSString *action, MPRemoteCommand *command, BOOL *stop) {
    command.enabled = YES;
    [command addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
      [weakSelf command:action value:0];
      return MPRemoteCommandHandlerStatusSuccess;
    }];
  }];
  center.changePlaybackPositionCommand.enabled = YES;
  [center.changePlaybackPositionCommand
      addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        MPChangePlaybackPositionCommandEvent *seek = (MPChangePlaybackPositionCommandEvent *)event;
        [weakSelf command:@"seek" value:seek.positionTime];
        return MPRemoteCommandHandlerStatusSuccess;
      }];
  // ±15 s o'rniga ⏮ ⏭ ko'rinsin
  center.skipForwardCommand.enabled = NO;
  center.skipBackwardCommand.enabled = NO;
  [UIApplication.sharedApplication beginReceivingRemoteControlEvents];
}

// Ekran qulfi uchun muqova (16:9)
- (void)loadArt:(NSString *)videoId {
  NSString *address = [NSString stringWithFormat:@"https://i.ytimg.com/vi/%@/mqdefault.jpg", videoId];
  NSURL *url = [NSURL URLWithString:address];
  if (!url) return;
  __weak Player *weakSelf = self;
  [[NSURLSession.sharedSession dataTaskWithURL:url
                             completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
                               UIImage *image = data ? [UIImage imageWithData:data] : nil;
                               if (!image) return;
                               dispatch_async(dispatch_get_main_queue(), ^{
                                 Player *strongSelf = weakSelf;
                                 if (!strongSelf || ![videoId isEqualToString:strongSelf.videoId]) return;
                                 strongSelf->_art = image;
                                 [strongSelf publish];
                               });
                             }] resume];
}

#pragma mark - havolalar

+ (NSString *)youtubeUrlFromText:(NSString *)text {
  if (text.length == 0) return nil;
  NSDataDetector *detector = [NSDataDetector dataDetectorWithTypes:NSTextCheckingTypeLink error:nil];
  for (NSTextCheckingResult *match in [detector matchesInString:text
                                                        options:0
                                                          range:NSMakeRange(0, text.length)]) {
    NSURLComponents *c = [NSURLComponents componentsWithURL:match.URL resolvingAgainstBaseURL:NO];
    NSString *host = c.host.lowercaseString ?: @"";
    if ([host isEqualToString:@"youtu.be"]) {
      NSString *videoId = [c.path stringByTrimmingCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@"/"]];
      if (videoId.length > 0) return [@"https://m.youtube.com/watch?v=" stringByAppendingString:videoId];
    } else if ([host isEqualToString:@"youtube.com"] || [host hasSuffix:@".youtube.com"]) {
      c.scheme = @"https";
      c.host = @"m.youtube.com";
      return c.URL.absoluteString;
    }
  }
  return nil;
}

@end
