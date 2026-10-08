#import <UIKit/UIKit.h>
#import <WebKit/WebKit.h>

NS_ASSUME_NONNULL_BEGIN

@protocol PlayerListener <NSObject>
- (void)playerDidChange;
@end

// Pleyerning yagona markazi (Android'dagi Player.java'ning iOS nusxasi). Faqat main thread'da.
//
// Holat YouTube sahifasidan keladi (yt-bridge.js → YtWebViewUI → onPage:) va tarqaladi:
// Lynx UI (PlayerListener = YtWebViewUI) va ekran qulfi (MPNowPlayingInfoCenter).
// Buyruqlar: Lynx tugmalari (PlayerModule) va ekran qulfi/naushnik (MPRemoteCommandCenter).
@interface Player : NSObject

+ (instancetype)shared;

@property(nonatomic, copy, readonly) NSString *url;
@property(nonatomic, copy, readonly) NSString *videoId;
@property(nonatomic, copy, readonly) NSString *title;
@property(nonatomic, copy, readonly) NSString *channel;
@property(nonatomic, readonly) BOOL canGoBack;
@property(nonatomic, readonly) BOOL playing;
@property(nonatomic, readonly) double position;  // soniya
@property(nonatomic, readonly) double duration;  // soniya, jonli efirda 0

- (void)attachWebView:(WKWebView *)webView listener:(id<PlayerListener>)listener;
- (void)detachWebView:(WKWebView *)webView;

// yt-bridge.js: {"id","title","channel","playing","pos","dur"}
- (void)onPage:(NSString *)json;
- (void)onNavigation:(NSString *)url canGoBack:(BOOL)canGoBack;

// play, pause, toggle, next, prev, seek (soniya), skip (+/- soniya), sleep (daqiqa), back
- (void)command:(NSString *)action value:(double)value;
- (void)load:(NSString *)url;

// Tashqaridan kelgan havola (ytlynx://open?url=...). WebView hali yo'q bo'lsa — keyinroq ochiladi.
- (void)open:(NSString *)url;
- (NSString *)takePendingUrl:(NSString *)fallback;

- (NSInteger)sleepLeftSeconds;
- (NSDictionary<NSString *, id> *)stateDictionary;

// Matndan birinchi YouTube havolasini topib, mobil sayt manziliga aylantiradi
+ (nullable NSString *)youtubeUrlFromText:(nullable NSString *)text;

@end

NS_ASSUME_NONNULL_END
