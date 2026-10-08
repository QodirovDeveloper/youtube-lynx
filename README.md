# YT Lynx

Shaxsiy YouTube brauzer-ilova (Lynx.js + Android). Ekran o'chganda, qulflanganda yoki boshqa
ilovaga o'tganda ham ovoz davom etadi; ekran qulfida ⏮ ⏯ ⏭ tugmalari bor.
Faqat o'zim uchun — Play Market'ga chiqarilmaydi.

## Imkoniyatlar

- **Fonda ijro** — ekran o'chsa ham, boshqa ilovada ham.
- **Ekran qulfi va bildirishnoma**: ⏮ ⏯ ⏭, muqova, nom, kanal, vaqt chizig'i, **Chiqish**.
  Bluetooth-naushnik tugmalari ham ishlaydi.
- **Qidiruv qatori** — so'z yozing yoki YouTube havolasini qo'ying.
- **Pastki pleyer paneli**: ♡ saqlash, −10 / +10 soniya, ⏮ ⏯ ⏭, ☾ uxlash taymeri (15 → 30 → 60 daqiqa).
- **Kutubxona** (☰): saqlanganlar va ko'rish tarixi.
- **Ulashish**: boshqa ilovada YouTube havolasini "Ulashish → YT Lynx" — shu yerda ochiladi.
- Telefonning "orqaga" tugmasi ilovani yopmaydi — fonga o'tkazadi, ovoz davom etadi.

## O'rnatish

Har `push`'da GitHub Actions APK'ni yig'adi va **Releases → latest** ga `yt-lynx.apk` qilib qo'yadi
(yonida emulyatordagi skrinshot va diagnostika).

1. Telefonda `yt-lynx.apk` ni yuklab, o'rnating ("noma'lum manbalar"ga ruxsat bering).
2. Birinchi ochilishda: bildirishnomaga **ruxsat bering**, "fonda cheklovsiz ishlasin" so'roviga **ha** deng.

Xiaomi/Redmi/POCO'da baribir to'xtab qolsa: Sozlamalar → Ilovalar → YT Lynx →
Batareya → **Cheklovsiz**, va **Avtoishga tushirish** yoqilsin.

## Qanday sinash kerak

- [ ] Video qo'ying → ekranni qulflang → 30+ daqiqa ovoz kelishi kerak.
- [ ] Ekran qulfida ⏸ / ▶ / ⏭ / ⏮ ishlaydi, nom va muqova to'g'ri.
- [ ] Boshqa ilovaga o'ting → ovoz davom etadi.
- [ ] Video tugasa, keyingisi o'zi boshlanadi (ekran o'chiq holda ham).
- [ ] Qo'ng'iroq kelsa yoki boshqa ilova musiqa qo'ysa — pauza bo'ladi.
- [ ] ☾ 15 → 15 daqiqadan keyin pauza.
- [ ] ♡ → ☰ Saqlangan ro'yxatida paydo bo'ladi, ilovani qayta ochganda ham turadi.
- [ ] Bildirishnomadagi **Chiqish** — ovoz to'xtaydi, ilova yopiladi.

## Qanday ishlaydi

```
┌──────────── Lynx (src/, ReactLynx) ────────────┐
│ TopBar: ‹  ▶  [ qidiruv ]  ☰                   │
│ <yt-webview>  ← o'zimizning native element     │──── bindstate: holat (nom, ijro, vaqt...)
│ PlayerBar: ♡ −10 ⏮ ⏯ ⏭ +10 ☾                  │──── NativeModules.PlayerModule.command()
└────────────────────────────────────────────────┘
           │                         ▲
           ▼                         │
  Player.java (yagona markaz) ── MediaSession ── ekran qulfi, naushnik
           │                  └─ PlaybackService ── bildirishnoma, foreground-servis
           ▼
  YtWebView.java ── m.youtube.com + assets/yt-bridge.js
```

Asosiy hiylalar:

1. `YtWebView.onWindowVisibilityChanged` — WebView'ga doim "ko'rinib turibsan" deydi, aks holda
   ekran o'chganda Chromium sahifani yashirin qiladi va YouTube pauza bosadi.
2. `yt-bridge.js` — sahifaga YouTube skriptlaridan **oldin** qo'shiladi: `document.hidden = false`,
   `<video>` hodisalarini kuzatib holatni Java'ga yuboradi, ⏮ ⏭ uchun YouTube'ning o'z
   `mediaSession` funksiyalarini ishlatadi.
3. `PlaybackService` — `mediaPlayback` turidagi foreground-servis: Android jarayonni o'ldirmaydi.
   Ijro paytida protsessor va Wi-Fi uyg'oq turadi, pauzada qo'yib yuboriladi.

## Tuzilma

| Fayl | Vazifasi |
| --- | --- |
| `src/App.tsx` | Ekran: yuqori panel, `<yt-webview>`, kutubxona, pleyer paneli |
| `src/components/` | `TopBar`, `PlayerBar`, `LibraryPanel`, CSS ikonkalar (`Icon`) |
| `src/lib/player.ts` | Holat turi, qidiruv/havola, vaqt formati, uxlash taymeri qadamlari |
| `src/lib/library.ts` | Saqlanganlar va tarix (toza funksiyalar, testlangan) |
| `src/lib/native.ts` | Android modullariga ko'prik |
| `android/.../YtWebViewUI.java` | `<yt-webview>` Lynx elementi |
| `android/.../YtWebView.java` | Fonda o'chmaydigan WebView |
| `android/.../Player.java` | Holat, buyruqlar, MediaSession, uxlash taymeri |
| `android/.../PlaybackService.java` | Bildirishnoma va foreground-servis |
| `android/app/src/main/assets/yt-bridge.js` | YouTube sahifasidagi ko'prik |

## Ishlab chiqish

```bash
npm install
npm test          # mantiq testlari
npm run build     # Lynx bundle (+ brauzer preview)
npm run dev       # brauzerda: /__web_preview?casename=main.web.bundle
```

Brauzer preview'da faqat Lynx UI ko'rinadi (YouTube oynasi va pleyer — faqat Android'da).

## iOS (keyinroq)

Mac kerak (Xcode). Lynx UI (`src/`) o'zgarmaydi; `WKWebView` + `AVAudioSession` (background audio)
+ `MPRemoteCommandCenter` (ekran qulfi) bilan iOS qobig'i yoziladi va AltStore/SideStore orqali
o'rnatiladi.
