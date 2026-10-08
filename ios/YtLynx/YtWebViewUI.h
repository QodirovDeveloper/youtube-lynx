#import <Lynx/LynxUI.h>

#import "Player.h"

NS_ASSUME_NONNULL_BEGIN

// Lynx'dagi <yt-webview> tegi (ViewController'da ro'yxatdan o'tgan).
//
//   <yt-webview blocked={...} bindstate={(e) => e.detail} />
//
// Ichida YouTube'ni ochadigan WKWebView turadi; holatni Player'dan olib, "state" hodisasi
// sifatida JS'ga yuboradi.
@interface YtWebViewUI : LynxUI <UIView *> <PlayerListener>
@end

NS_ASSUME_NONNULL_END
