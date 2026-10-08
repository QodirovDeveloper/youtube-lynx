#import "ViewController.h"

#import <Lynx/LynxConfig.h>
#import <Lynx/LynxError.h>
#import <Lynx/LynxView.h>
#import <Lynx/LynxViewClient.h>
#import <XElement/LynxUIInput.h>

#import "PlayerModule.h"
#import "StorageModule.h"
#import "TemplateProvider.h"
#import "YtWebViewUI.h"

static UIColor *SurfaceColor(void) {
  return [UIColor colorWithRed:24 / 255.0 green:24 / 255.0 blue:29 / 255.0 alpha:1];
}

@interface ViewController () <LynxViewLifecycle>
@end

@implementation ViewController {
  LynxView *_lynxView;
  CGRect _lastFrame;
  UIView *_errorPanel;
  UITextView *_errorText;
}

- (void)viewDidLoad {
  [super viewDidLoad];
  // status bar va pastki "home" chizig'i ostidagi joy panellar rangida bo'lsin
  self.view.backgroundColor = SurfaceColor();

  NSData *bundle = [TemplateProvider mainBundleData];
  if (!bundle) {
    [self showError:@"Resources/main.lynxbundle topilmadi"];
    return;
  }

  CGSize screen = UIScreen.mainScreen.bounds.size;
  _lynxView = [[LynxView alloc] initWithBuilderBlock:^(LynxViewBuilder *builder) {
    builder.config = [[LynxConfig alloc] initWithProvider:[[TemplateProvider alloc] init]];
    builder.screenSize = screen;
    builder.fontScale = 1.0;
    // bizning <yt-webview> tegimiz
    [builder.config registerUI:YtWebViewUI.class withName:@"yt-webview"];
    // <input> (qidiruv qatori): XElement'dan faqat Input qismi olingan, shuning uchun o'zimiz ulaymiz
    [builder.config registerUI:LynxUIInput.class withName:@"input"];
    [builder.config registerShadowNode:LynxUIInputShadowNode.class withName:@"input"];
    // JS'dagi NativeModules.PlayerModule / StorageModule
    [builder.config registerModule:PlayerModule.class];
    [builder.config registerModule:StorageModule.class];
  }];
  _lynxView.layoutWidthMode = LynxViewSizeModeExact;
  _lynxView.layoutHeightMode = LynxViewSizeModeExact;
  _lynxView.enableAutoLayout = YES;
  [_lynxView addLifecycleClient:self];
  [self.view addSubview:_lynxView];
  [self layoutLynxView];
  [_lynxView loadTemplate:bundle withURL:@"main.lynx.bundle"];
  [_lynxView triggerLayout];
}

- (void)viewDidLayoutSubviews {
  [super viewDidLayoutSubviews];
  [self layoutLynxView];
  _errorPanel.frame = UIEdgeInsetsInsetRect(self.view.bounds, self.view.safeAreaInsets);
}

// Lynx xavfsiz hudud ichida: tepada status bar, pastda "home" chizig'i
- (void)layoutLynxView {
  if (!_lynxView) return;
  CGRect frame = UIEdgeInsetsInsetRect(self.view.bounds, self.view.safeAreaInsets);
  if (CGRectEqualToRect(frame, _lastFrame)) return;
  _lastFrame = frame;
  _lynxView.frame = frame;
  _lynxView.preferredLayoutWidth = frame.size.width;
  _lynxView.preferredLayoutHeight = frame.size.height;
  [_lynxView updateScreenMetricsWithWidth:self.view.bounds.size.width
                                   height:self.view.bounds.size.height];
  [_lynxView updateViewportWithPreferredLayoutWidth:frame.size.width
                              preferredLayoutHeight:frame.size.height];
}

- (UIStatusBarStyle)preferredStatusBarStyle {
  return UIStatusBarStyleLightContent;
}

#pragma mark - xatolar

- (void)lynxView:(LynxView *)view didRecieveError:(NSError *)error {
  NSLog(@"[YtLynx] Lynx xatosi: %@", error);
  if ([error isKindOfClass:LynxError.class] && ((LynxError *)error).isFatal) {
    LynxError *lynxError = (LynxError *)error;
    [self showError:[NSString stringWithFormat:@"Lynx xatosi [%ld]:\n%@\n\n%@", (long)error.code,
                                               error.localizedDescription,
                                               lynxError.callStack ?: @""]];
  }
}

// Faqat jiddiy xato bo'lganda: sababini ko'rsatadi, nusxa olish va yopish mumkin
- (void)showError:(NSString *)text {
  dispatch_async(dispatch_get_main_queue(), ^{
    if (!self->_errorPanel) [self buildErrorPanel];
    NSString *old = self->_errorText.text ?: @"";
    self->_errorText.text = old.length ? [NSString stringWithFormat:@"%@\n\n%@", old, text] : text;
    self->_errorPanel.hidden = NO;
    [self.view bringSubviewToFront:self->_errorPanel];
  });
}

- (void)buildErrorPanel {
  _errorPanel = [[UIView alloc]
      initWithFrame:UIEdgeInsetsInsetRect(self.view.bounds, self.view.safeAreaInsets)];
  _errorPanel.backgroundColor = UIColor.whiteColor;

  UIButton *copy = [UIButton buttonWithType:UIButtonTypeSystem];
  [copy setTitle:@"Nusxa olish" forState:UIControlStateNormal];
  copy.frame = CGRectMake(16, 8, 120, 40);
  [copy addTarget:self action:@selector(copyError) forControlEvents:UIControlEventTouchUpInside];

  UIButton *close = [UIButton buttonWithType:UIButtonTypeSystem];
  [close setTitle:@"Yopish" forState:UIControlStateNormal];
  close.frame = CGRectMake(144, 8, 100, 40);
  [close addTarget:self action:@selector(closeError) forControlEvents:UIControlEventTouchUpInside];

  _errorText = [[UITextView alloc] initWithFrame:CGRectMake(0, 56, _errorPanel.bounds.size.width,
                                                            _errorPanel.bounds.size.height - 56)];
  _errorText.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  _errorText.editable = NO;
  _errorText.textColor = UIColor.redColor;
  _errorText.font = [UIFont monospacedSystemFontOfSize:12 weight:UIFontWeightRegular];

  [_errorPanel addSubview:copy];
  [_errorPanel addSubview:close];
  [_errorPanel addSubview:_errorText];
  [self.view addSubview:_errorPanel];
}

- (void)copyError {
  UIPasteboard.generalPasteboard.string = _errorText.text;
}

- (void)closeError {
  _errorPanel.hidden = YES;
}

@end
