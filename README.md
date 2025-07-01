# AR Video Uygulaması

Bu Android uygulaması, kamera ile belirli bir fotoğrafı tanıyarak üzerine video oynatır (Augmented Reality).

## Özellikler

- **Gerçek Zamanlı Fotoğraf Tanıma**: Kamera ile gelen görüntüleri hedef fotoğraf ile karşılaştırır
- **AR Video Oynatma**: Hedef fotoğraf tanındığında üzerine video oynatır
- **Akıllı Eşleştirme**: İki farklı algoritma (piksel karşılaştırma ve histogram analizi) kullanarak güvenilir tanıma
- **Modern UI**: Jetpack Compose ile oluşturulmuş kullanıcı dostu arayüz

## Gereksinimler

- Android API 24 (Android 7.0) ve üzeri
- Kamera izni
- Minimum 2GB RAM önerilir

## Kurulum

1. Projeyi Android Studio'da açın
2. Gerekli dosyaları ekleyin:
   - `app/src/main/assets/target_image.jpg` - Tanınacak hedef fotoğraf
   - `app/src/main/res/raw/ar_video.mp4` - Oynatılacak video
3. Uygulamayı derleyin ve çalıştırın

## Kullanım

1. Uygulamayı açın
2. "AR Kamerayı Başlat" butonuna tıklayın
3. Kamera izni verin
4. Hedef fotoğrafı kameraya gösterin
5. Uygulama fotoğrafı tanıyınca otomatik olarak video oynatılacak

## Dosya Yapısı

```
app/
├── src/main/
│   ├── java/com/example/arvideo/
│   │   ├── MainActivity.kt          # Ana ekran
│   │   ├── ArCameraActivity.kt      # AR kamera ekranı
│   │   └── ImageMatcher.kt          # Görüntü eşleştirme algoritmaları
│   ├── assets/
│   │   └── target_image.jpg         # Hedef fotoğraf (sizin eklemeniz gerekir)
│   └── res/raw/
│       └── ar_video.mp4             # AR videosu (sizin eklemeniz gerekir)
```

## Teknik Detaylar

### Kullanılan Teknolojiler

- **Jetpack Compose**: Modern UI framework
- **CameraX**: Kamera API
- **ExoPlayer**: Video oynatma
- **ML Kit**: Görüntü işleme (fallback)
- **Custom Image Matching**: Piksel ve histogram tabanlı eşleştirme

### Görüntü Eşleştirme Algoritması

1. **Piksel Tabanlı Karşılaştırma**: Her pikselin RGB değerleri karşılaştırılır
2. **Histogram Analizi**: Renk dağılımı karşılaştırılır
3. **Hibrit Yaklaşım**: İki yöntemin ortalaması alınır

### Performans Optimizasyonları

- Görüntüler işleme öncesi 50x50 piksel boyutuna küçültülür
- Arkaplan thread'de işleme yapılır
- Gereksiz hesaplamalar önlenir

## Özelleştirme

### Hedef Fotoğraf Değiştirme

1. Yeni fotoğrafı `app/src/main/assets/target_image.jpg` olarak kaydedin
2. Fotoğraf net ve karakteristik özellikler içermeli
3. Uygulamayı yeniden derleyin

### Video Değiştirme

1. Yeni videoyu `app/src/main/res/raw/ar_video.mp4` olarak kaydedin
2. Video formatının MP4 olduğundan emin olun
3. Dosya boyutunu makul tutun (< 50MB)

### Eşik Değerleri Ayarlama

`ImageMatcher.kt` dosyasında:
```kotlin
private const val MATCH_THRESHOLD = 0.75f  // Eşleştirme eşiği
```

`ArCameraActivity.kt` dosyasında:
```kotlin
if (averageSimilarity > 0.6f && !isVideoPlaying) {  // Video başlatma eşiği
    startVideo()
} else if (averageSimilarity <= 0.4f && isVideoPlaying) {  // Video durdurma eşiği
    stopVideo()
}
```

## Sorun Giderme

### Video Oynatılmıyor
- Video dosyasının `app/src/main/res/raw/` klasöründe olduğundan emin olun
- Video formatının MP4 olduğunu kontrol edin
- Dosya boyutunun çok büyük olmadığından emin olun

### Fotoğraf Tanınmıyor
- Hedef fotoğrafın `app/src/main/assets/` klasöründe olduğundan emin olun
- Fotoğrafın net ve iyi ışıklandırılmış olduğundan emin olun
- Eşik değerlerini düşürmeyi deneyin

### Kamera Açılmıyor
- Kamera izninin verildiğinden emin olun
- Başka bir uygulamanın kamerayı kullanmadığından emin olun
- Cihazı yeniden başlatmayı deneyin

## Katkıda Bulunma

1. Projeyi fork edin
2. Feature branch oluşturun (`git checkout -b feature/AmazingFeature`)
3. Değişikliklerinizi commit edin (`git commit -m 'Add some AmazingFeature'`)
4. Branch'inizi push edin (`git push origin feature/AmazingFeature`)
5. Pull Request oluşturun

## Lisans

Bu proje MIT lisansı altında dağıtılmaktadır. Detaylar için `LICENSE` dosyasını inceleyebilirsiniz.

## İletişim

Proje Sahibi - [GitHub](https://github.com/yourusername/arvideo)

Proje Linki: [https://github.com/yourusername/arvideo](https://github.com/yourusername/arvideo) 