#import "YtWebViewUI.h"

#import <Lynx/LynxEvent.h>
#import <Lynx/LynxEventEmitter.h>
#import <Lynx/LynxPropsProcessor.h>
#import <Lynx/LynxUIContext.h>
#import <WebKit/WebKit.h>

static NSString *const kHome = @"https://m.youtube.com/";
static NSString *const kHandler = @"YtNative";

// yt-bridge.js Android'dagi YtNative.postMessage(json) ni chaqiradi; iOS'da u
// window.webkit.messageHandlers.YtNative orqali ishlaydi
static NSString *const kShim =
    @"if(!window.YtNative&&window.webkit&&window.webkit.messageHandlers&&"
     "window.webkit.messageHandlers.YtNative){window.YtNative={postMessage:function(s){"
     "window.webkit.messageHandlers.YtNative.postMessage(s);}};}\n";

static UIColor *BackgroundColor(void) {
  return [UIColor colorWithRed:15 / 255.0 green:15 / 255.0 blue:18 / 255.0 alpha:1];
}

// WKUserContentController handler'ni kuchli ushlab qoladi — aylanma bog'lanish bo'lmasligi uchun
// zaif vositachi orqali beramiz
@interface YtWeakScriptHandler : NSObject <WKScriptMessageHandler>
@property(nonatomic, weak) id<WKScriptMessageHandler> target;
@end

@implementation YtWeakScriptHandler
- (void)userContentController:(WKUserContentController *)controller
      didReceiveScriptMessage:(WKScriptMessage *)message {
  [self.target userContentController:controller didReceiveScriptMessage:message];
}
@end

@interface YtWebViewUI () <WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler>
@end

@implementation YtWebViewUI {
  WKWebView *_web;
}

- (UIView *)createView {
  UIView *box = [[UIView alloc] init];
  box.backgroundColor = BackgroundColor();
  box.clipsToBounds = YES;

  _web = [self makeWebView];
  _web.frame = box.bounds;
  _web.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  [box addSubview:_web];

  [[Player shared] attachWebView:_web listener:self];
  NSURL *url = [NSURL URLWithString:[[Player shared] takePendingUrl:kHome]];
  [_web loadRequest:[NSURLRequest requestWithURL:url ?: [NSURL URLWithString:kHome]]];
  return box;
}

- (WKWebView *)makeWebView {
  WKWebViewConfiguration *config = [[WKWebViewConfiguration alloc] init];
  // video sahifa ichida o'ynasin (iPhone'da odatiy holat — darhol to'liq ekran)
  config.allowsInlineMediaPlayback = YES;
  config.allowsPictureInPictureMediaPlayback = YES;
  // keyingi video ekranga bosmasdan ham boshlansin
  config.mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone;
  // YouTube va Google bizni oddiy Safari deb bilsin
  config.applicationNameForUserAgent = @"Version/17.0 Mobile/15E148 Safari/604.1";
  if (@available(iOS 17.0, *)) {
    // ilova faol bo'lmaganda ham sahifa to'xtatilmasin
    config.preferences.inactiveSchedulingPolicy = WKInactiveSchedulingPolicyNone;
  }

  NSString *path = [[NSBundle mainBundle] pathForResource:@"yt-bridge" ofType:@"js"];
  NSString *bridge = path ? [NSString stringWithContentsOfFile:path
                                                      encoding:NSUTF8StringEncoding
                                                         error:nil]
                          : nil;
  if (!bridge) NSLog(@"[YtLynx] yt-bridge.js topilmadi");
  // forMainFrameOnly:NO — "ko'rinib turibman" hiylasi iframe'larda ham kerak
  WKUserScript *script =
      [[WKUserScript alloc] initWithSource:[kShim stringByAppendingString:bridge ?: @""]
                             injectionTime:WKUserScriptInjectionTimeAtDocumentStart
                          forMainFrameOnly:NO];
  [config.userContentController addUserScript:script];
  YtWeakScriptHandler *handler = [[YtWeakScriptHandler alloc] init];
  handler.target = self;
  [config.userContentController addScriptMessageHandler:handler name:kHandler];

  WKWebView *web = [[WKWebView alloc] initWithFrame:CGRectZero configuration:config];
  web.navigationDelegate = self;
  web.UIDelegate = self;
  web.allowsBackForwardNavigationGestures = YES;
  web.opaque = NO;
  web.backgroundColor = BackgroundColor();
  web.scrollView.backgroundColor = BackgroundColor();
  if (@available(iOS 16.4, *)) {
    // Mac'dagi Safari → Develop menyusi orqali sahifani ko'rish mumkin bo'lsin
    web.inspectable = YES;
  }
  [web addObserver:self forKeyPath:@"URL" options:NSKeyValueObservingOptionNew context:nil];
  [web addObserver:self forKeyPath:@"canGoBack" options:NSKeyValueObservingOptionNew context:nil];
  return web;
}

- (void)layoutDidFinished {
  [super layoutDidFinished];
  _web.frame = self.view.bounds;
}

// true: ustida Lynx paneli ochiq, WebView bosishlarni qabul qilmaydi
LYNX_PROP_SETTER("blocked", setBlocked, BOOL) {
  BOOL blocked = requestReset ? NO : value;
  self.view.userInteractionEnabled = !blocked;
}

#pragma mark - holat

// YouTube bitta sahifali ilova: video almashganda URL o'zgaradi, sahifa esa qayta yuklanmaydi
- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary<NSKeyValueChangeKey, id> *)change
                       context:(void *)context {
  [[Player shared] onNavigation:_web.URL.absoluteString ?: @"" canGoBack:_web.canGoBack];
}

- (void)userContentController:(WKUserContentController *)controller
      didReceiveScriptMessage:(WKScriptMessage *)message {
  // faqat YouTube'ning asosiy sahifasidan kelgan xabarlarga ishonamiz
  if (!message.frameInfo.isMainFrame) return;
  NSString *host = message.frameInfo.securityOrigin.host.lowercaseString ?: @"";
  if (![host isEqualToString:@"youtube.com"] && ![host hasSuffix:@".youtube.com"]) return;
  if ([message.body isKindOfClass:NSString.class]) [[Player shared] onPage:message.body];
}

- (void)playerDidChange {
  // LynxDetailEvent: JS'da e.detail bo'lib keladi
  LynxDetailEvent *event = [[LynxDetailEvent alloc] initWithName:@"state"
                                                      targetSign:self.sign
                                                          detail:[[Player shared] stateDictionary]];
  [self.context.eventEmitter dispatchCustomEvent:event];
}

#pragma mark - WKNavigationDelegate / WKUIDelegate

- (void)webView:(WKWebView *)webView
    decidePolicyForNavigationAction:(WKNavigationAction *)action
                    decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
  NSString *scheme = action.request.URL.scheme.lowercaseString ?: @"";
  NSSet *allowed = [NSSet setWithArray:@[ @"http", @"https", @"about", @"blob", @"data" ]];
  if (![allowed containsObject:scheme]) {
    // youtube://, intent:// — rasmiy YouTube ilovasini ochishga urinish. Bizda qolamiz.
    decisionHandler(WKNavigationActionPolicyCancel);
    return;
  }
  // Allow + 2 = _WKNavigationActionPolicyAllowWithoutTryingAppLink (WebKit'ning yopiq qiymati,
  // Firefox iOS ham ishlatadi): youtube.com havolasi o'rnatilgan YouTube ilovasida ochilib ketmasin
  decisionHandler((WKNavigationActionPolicy)(WKNavigationActionPolicyAllow + 2));
}

// target="_blank" havolalar ham shu oynada ochilsin
- (WKWebView *)webView:(WKWebView *)webView
    createWebViewWithConfiguration:(WKWebViewConfiguration *)configuration
               forNavigationAction:(WKNavigationAction *)navigationAction
                    windowFeatures:(WKWindowFeatures *)windowFeatures {
  if (navigationAction.request.URL) [webView loadRequest:navigationAction.request];
  return nil;
}

// sahifa jarayoni o'ldi (xotira yetmadi) — qayta yuklaymiz
- (void)webViewWebContentProcessDidTerminate:(WKWebView *)webView {
  NSLog(@"[YtLynx] WebContent jarayoni o'ldi, qayta yuklaymiz");
  [webView reload];
}

- (void)dealloc {
  [_web removeObserver:self forKeyPath:@"URL"];
  [_web removeObserver:self forKeyPath:@"canGoBack"];
  [_web.configuration.userContentController removeScriptMessageHandlerForName:kHandler];
  [[Player shared] detachWebView:_web];
}

@end
