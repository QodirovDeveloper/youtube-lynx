// YouTube sahifasiga, uning o'z skriptlaridan OLDIN qo'shiladi (YtWebView.java).
//  1) Sahifa doim "ko'rinib turibdi" deb o'ylasin — YouTube fonda pauza qilmasin.
//  2) Pleyer holatini Java'ga yuboradi: YtNative.postMessage(JSON) → Player.onPage().
//  3) Java'dan buyruq oladi: window.__ytCmd('next', 0).
(function () {
  if (window.__ytBridge) return;
  window.__ytBridge = true;

  // ------------------------------------------------------------ 1. ko'rinish
  function always(obj, name, value) {
    try {
      Object.defineProperty(obj, name, { get: function () { return value; }, configurable: true });
    } catch (e) {}
  }
  always(document, 'hidden', false);
  always(document, 'webkitHidden', false);
  always(document, 'visibilityState', 'visible');
  always(document, 'webkitVisibilityState', 'visible');
  document.hasFocus = function () { return true; };

  // "sahifa yashirindi" signallarini YouTube'ga yetkazmaymiz.
  // window'dagi capture-tinglovchi har qanday document tinglovchisidan oldin ishlaydi.
  function swallow(e) { e.stopImmediatePropagation(); }
  window.addEventListener('visibilitychange', swallow, true);
  window.addEventListener('webkitvisibilitychange', swallow, true);
  // faqat oynaning o'zi fokusni yo'qotganda (input'lardagi blur'ga tegmaymiz)
  window.addEventListener('blur', function (e) { if (e.target === window) swallow(e); }, true);

  // reklama/iframe'larda faqat ko'rinish hiylasi yetarli
  if (window.top !== window) return;

  // "Hali ko'ryapsizmi?" so'rovi chiqmasin: YouTube oxirgi faollik vaqtini shu yerdan o'qiydi
  setInterval(function () { window._lact = Date.now(); }, 60000);

  // ------------------------------------------------------------ 2. YouTube'ning ⏮ ⏭ mantig'i
  // YouTube navigator.mediaSession'ga "keyingi/oldingi" funksiyalarini beradi — ularni eslab qolamiz.
  // WebView'da mediaSession bo'lmasa, soxtasini qo'yamiz, shunda YouTube baribir beradi.
  var handlers = {};
  var ms = navigator.mediaSession;
  if (!ms) {
    ms = { metadata: null, playbackState: 'none' };
    try { Object.defineProperty(navigator, 'mediaSession', { value: ms, configurable: true }); } catch (e) {}
    if (!window.MediaMetadata) {
      window.MediaMetadata = function (init) {
        init = init || {};
        this.title = init.title || '';
        this.artist = init.artist || '';
        this.album = init.album || '';
        this.artwork = init.artwork || [];
      };
    }
  }
  var nativeSetHandler = typeof ms.setActionHandler === 'function' ? ms.setActionHandler.bind(ms) : null;
  ms.setActionHandler = function (action, fn) {
    handlers[action] = fn;
    if (nativeSetHandler) { try { nativeSetHandler(action, fn); } catch (e) {} }
  };
  ['setPositionState', 'setCameraActive', 'setMicrophoneActive'].forEach(function (name) {
    if (typeof ms[name] !== 'function') ms[name] = function () {};
  });

  // ------------------------------------------------------------ 3. holat
  function player() { return document.getElementById('movie_player'); }

  function video() {
    return document.querySelector('#movie_player video') ||
      document.querySelector('video.html5-main-video') ||
      document.querySelector('video');
  }

  function idFromUrl(href) {
    try {
      var u = new URL(href, location.href);
      var m = u.pathname.match(/^\/(?:shorts|live|embed)\/([\w-]{11})/);
      if (m) return m[1];
      var v = u.searchParams.get('v');
      return v && /^[\w-]{11}$/.test(v) ? v : '';
    } catch (e) {
      return '';
    }
  }

  function textOf(selector) {
    var el = document.querySelector(selector);
    return el ? (el.textContent || '').trim() : '';
  }

  function info() {
    var v = video();
    var d = null;
    try {
      var p = player();
      d = p && typeof p.getVideoData === 'function' ? p.getVideoData() : null;
    } catch (e) {}
    var meta = navigator.mediaSession && navigator.mediaSession.metadata;
    var hasMedia = !!(v && (v.currentSrc || v.src));
    var id = (d && d.video_id) || idFromUrl(location.href);
    return {
      id: hasMedia ? id : '',
      title: (d && d.title) || (meta && meta.title) || document.title.replace(/\s*-\s*YouTube\s*$/, ''),
      channel: (d && d.author) || (meta && meta.artist) || textOf('.slim-owner-channel-name'),
      playing: !!(v && !v.paused && !v.ended),
      pos: v ? v.currentTime || 0 : 0,
      dur: v && isFinite(v.duration) ? v.duration : 0
    };
  }

  var lastKey = '';
  var lastAt = 0;
  // iOS: ilova fonga o'tganda tizim videoni o'zi pauza qiladi — 'resume' buyrug'i uchun kerak
  var lastPlayingAt = 0;
  var userPausedAt = 0;
  var touchedAt = 0;
  function send(force) {
    var s = info();
    var key = s.id + '|' + s.title + '|' + s.channel + '|' + s.playing + '|' + Math.round(s.dur);
    var now = Date.now();
    if (s.playing) lastPlayingAt = now;
    // o'zgarishda darhol; ijro paytida esa soniyada bir marta (progress chizig'i uchun)
    if (!force && key === lastKey && !(s.playing && now - lastAt >= 900)) return;
    lastKey = key;
    lastAt = now;
    if (s.id) remember(s.id);
    try { YtNative.postMessage(JSON.stringify(s)); } catch (e) {}
  }

  // media hodisalari ko'tarilmaydi (bubble), lekin capture bosqichida document ularni ko'radi
  ['play', 'playing', 'pause', 'ended', 'loadedmetadata', 'durationchange', 'seeked', 'emptied']
    .forEach(function (type) {
      document.addEventListener(type, function () { send(true); }, true);
    });
  window.addEventListener('yt-navigate-finish', function () { setTimeout(function () { send(true); }, 500); });
  setInterval(function () { send(false); }, 1000);

  // Pauzani foydalanuvchi o'zi bosdimi (sahifadagi tugma) yoki tizim qildimi — ajratish uchun
  ['touchstart', 'pointerdown', 'keydown'].forEach(function (type) {
    document.addEventListener(type, function () { touchedAt = Date.now(); }, true);
  });
  document.addEventListener('pause', function () {
    if (Date.now() - touchedAt < 1500) userPausedAt = Date.now();
  }, true);

  // Ilova ichidan ochilgan video sahifasi (tarix, saqlanganlar) o'zi boshlanmasa — bir marta boshlaymiz
  var autoplayUntil = Date.now() + 15000;
  document.addEventListener('canplay', function (e) {
    var v = e.target;
    if (Date.now() > autoplayUntil || !idFromUrl(location.href) || !v.paused || v.ended) return;
    autoplayUntil = 0;
    play(v);
  }, true);

  // ------------------------------------------------------------ 4. "oldingi" uchun ko'rilganlar
  var PLAYED = '__ytPlayed';
  var lastRemembered = '';
  function played() {
    try { return JSON.parse(sessionStorage.getItem(PLAYED) || '[]'); } catch (e) { return []; }
  }
  function savePlayed(list) {
    try { sessionStorage.setItem(PLAYED, JSON.stringify(list.slice(-50))); } catch (e) {}
  }
  function remember(id) {
    if (id === lastRemembered) return;
    lastRemembered = id;
    var list = played();
    if (list[list.length - 1] !== id) {
      list.push(id);
      savePlayed(list);
    }
  }

  // ------------------------------------------------------------ 5. buyruqlar
  function play(v) {
    var r = v.play();
    if (r && r.catch) r.catch(function () {});
  }

  function next() {
    var beforeId = info().id;
    var beforeUrl = location.href;
    if (handlers.nexttrack) {
      try { handlers.nexttrack({ action: 'nexttrack' }); } catch (e) {}
    } else {
      try { var p = player(); if (p && p.nextVideo) p.nextVideo(); } catch (e) {}
    }
    // YouTube hech narsa qilmadi — tavsiya qilingan birinchi videoni ochamiz
    setTimeout(function () {
      if (location.href === beforeUrl && info().id === beforeId) openRelated(beforeId);
    }, 1500);
  }

  function openRelated(currentId) {
    var links = document.querySelectorAll('a[href*="/watch?v="]');
    for (var i = 0; i < links.length; i++) {
      var id = idFromUrl(links[i].href);
      if (id && id !== currentId) {
        links[i].click();
        return;
      }
    }
  }

  function prev() {
    var v = video();
    // odatdagi pleyerlardagidek: video o'rtasida bo'lsa — boshiga qaytaramiz
    if (v && v.currentTime > 5) {
      v.currentTime = 0;
      return;
    }
    if (handlers.previoustrack) {
      try { handlers.previoustrack({ action: 'previoustrack' }); return; } catch (e) {}
    }
    var list = played();
    var current = info().id;
    if (list.length && list[list.length - 1] === current) list.pop();
    var id = list.pop();
    if (id) {
      savePlayed(list);
      location.assign('/watch?v=' + id);
    } else if (v) {
      v.currentTime = 0;
    }
  }

  window.__ytCmd = function (action, value) {
    var v = video();
    switch (action) {
      case 'play': if (v) play(v); break;
      case 'pause': userPausedAt = Date.now(); if (v) v.pause(); break;
      case 'toggle':
        if (!v) break;
        if (v.paused || v.ended) {
          play(v);
        } else {
          userPausedAt = Date.now();
          v.pause();
        }
        break;
      // iOS: fonga o'tgandan keyin. Video hozirgina o'ynayotgan bo'lsa va pauzani
      // foydalanuvchi bosmagan bo'lsa — davom ettiramiz.
      case 'resume':
        if (v && v.paused && !v.ended && Date.now() - lastPlayingAt < 6000 && userPausedAt < lastPlayingAt) {
          play(v);
        }
        break;
      case 'next': next(); break;
      case 'prev': prev(); break;
      case 'seek': if (v) v.currentTime = Math.max(0, value); break;
      case 'skip': if (v) v.currentTime = Math.max(0, v.currentTime + value); break;
    }
    setTimeout(function () { send(true); }, 250);
  };
})();
