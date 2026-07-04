# Proje Nefes — Native Android PoC

## Nasıl derlenir (APK'yı sen çıkaracaksın)

Bu ortamda Android SDK/Gradle indirme erişimi olmadığı için APK'yı burada derleyemedim.
Şu adımları izleyerek kendi bilgisayarında gerçek bir APK çıkarabilirsin:

1. [Android Studio](https://developer.android.com/studio) kur (ücretsiz).
2. Bu klasörü (`ProjeNefes/`) **Open Project** ile aç.
3. Gradle senkronizasyonu bitince üstteki ▶ (Run) tuşuna bas veya
   **Build → Build Bundle(s)/APK(s) → Build APK(s)** ile APK üret.
4. Çıkan `app-debug.apk` dosyasını telefona (Oppo Find X9 Pro) yükle —
   "Bilinmeyen kaynaklardan yükleme" iznini açman gerekebilir.

## Bu PoC gerçekte ne yapıyor (dürüst kapsam)

| Özellik | Durum | Açıklama |
|---|---|---|
| Jiroskop/yön okuma | ✅ GERÇEK | `SensorManager`, `TYPE_ROTATION_VECTOR` |
| Wi-Fi RSSI taraması | ✅ GERÇEK | `WifiManager.getScanResults()` — çevredeki AP'lerin sinyal gücü |
| Kişisel Hotspot açma | ✅ GERÇEK | `WifiManager.startLocalOnlyHotspot()` — resmi Android API, ayarlara gitmeden, kullanıcı izniyle |
| Mikrofon vurma/darbe tespiti | ✅ GERÇEK | `AudioRecord` + RMS enerji eşiği — enkaz vurma sesi USAR'da bilinen bir yöntemdir |
| Görselleştirme | ✅ GERÇEK | Canvas tabanlı radar + darbe anında çöp adam, açıya göre konumlanır |
| Duvar arkası nefes tespiti (Wi-Fi CSI ile) | ❌ YOK | Tüketici telefonlarında CSI ham verisi erişilebilir değil (root + özel firmware + genelde 2 ayrı cihaz gerekir). Bu iddia edilen özellik bu PoC'ta **yoktur** ve olamaz. |
| Mesafe/derinlik ölçümü | ❌ YOK | Uygulama "hangi yönden ses geldi" bilgisini verir, "kaç metre ötede" bilgisini vermez. |

## Şantiye testinde nasıl kullanılır

1. Uygulamayı aç, üç izni de ver (konum, mikrofon, Wi-Fi).
2. "DİNLE" tuşuna bas — mikrofon gerçek zamanlı dinlemeye başlar, ilk ~1-2 saniye ortam
   gürültüsünü kalibre eder.
3. Enkaz/duvar arkasından biri vurunca (test: bir kişi diğer taraftan objeye vursun),
   ekranda gerçek zamanlı "DARBE ALGILANDI" yazısı ve çöp adam belirir.
4. Telefonu döndürerek farklı yönleri tara — radar çizgisi jiroskopla gerçek zamanlı döner.
5. "WI-FI TARA" ve "HOTSPOT AÇ" bağımsız, ortam/bağlantı bilgisi amaçlıdır — hayat tespiti
   ile doğrudan ilgili değildir.

## Sonraki gerçek adım

Vurma tespiti algoritması şu an basit bir enerji-eşiği. Şantiye testlerinden sonra
gerçek darbe kayıtları toplanırsa, bunun üzerine bir FFT tabanlı frekans-imza filtresi
(metal/beton vurma seslerinin karakteristik frekans bandını ayırt eden) eklenebilir —
bu, algoritmanın doğruluğunu ciddi şekilde artırır ama gerçek ses örnekleri gerektirir.
