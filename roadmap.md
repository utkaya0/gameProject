# Mini oyun platformu — 12 Aşamalı Yol Haritası


## Hedef

İlk aşamada yalnızca bir oyun olacak. Daha sonra farklı oyunlar eklenecek. İlk oyun, kısa süre gösterilen bir rengi **gizlendikten sonra hafızadan yeniden üretme** oyunudur. Tek kişilik oynanır veya bir oyuncu lobi kurar, diğerleri kodla katılır. Login/register yoktur. İlk sürümde yalnız bu oyun sunulur; yeni oyunlar için lobi kodunu yeniden yazmayı gerektirmeyen modül sınırları korunur.

Her aşama çalışır ve doğrulanabilir küçük bir çıktı verir. Teknik kararlar [architecture.md](architecture.md) dosyasındadır. Aşağıdaki süreler ve puan eğrisi ilk ürün varsayımlarıdır; kısa kullanıcı testiyle kalibre edilir.

## Aşamalar

### 1. Ürün sözleşmesi ve mimari

- **Çıktı:** Hafıza akışı, 5 raund, süreler, skor, anonim kimlik, lobi davranışı ve modül sınırları tanımlanır.
- **Kabul:** Hedefin ne zaman görünüp gizlendiği, tahminin ne zaman kabul edildiği ve oyunun nasıl bittiği belgelidir.
- **Durum:** Tamamlandı; bu plan ve `architecture.md` başlangıç sözleşmesidir. Süre ve puan eğrisi kullanıcı testiyle kesinleşecek varsayımlardır.

### 2. Çalışan proje iskeleti

- **Çıktı:** Gradle ile Spring Boot backend, React + TypeScript + Vite frontend ve yerel çalıştırma komutları kurulur; health endpoint'i eklenir.
- **Kabul:** Temiz ortamda iki uygulama açılır, frontend backend health yanıtını gösterir.
- **Durum:** Tamamlandı. Backend ve PostgreSQL temeli önceki çalışmada kurulmuştu; frontend iskeleti, `/api/v1/health` ve bağlantı durumu ekranı eklendi.

### 3. Renk motoru

- **Çıktı:** sRGB hedef üretimi, `#RRGGBB` doğrulaması, Lab dönüşümü ve CIEDE2000 temelli 0–10 puan hesabı bağımsız Java kodunda yazılır. `GameConfig` ilk değerleri taşır.
- **Kabul:** Tam eşleşme `10.00` puan verir; geçersiz renk reddedilir; referans renk çiftleri ve puan sınırları birim testlerinden geçer.
- **Sınır:** HTTP ve ekran yoktur.
- **Durum:** Tamamlandı. Renk motoru ve sürümlü başlangıç ayarları eklendi; 34 CIEDE2000 referans çifti dahil birim testleri geçiyor. `scale=20.0` ilk varsayımdır ve kullanıcı testiyle kalibre edilecektir.

### 4. Zamanlı tek kişilik API

- **Çıktı:** Oyun başlatma, durum okuma ve raund tahmini REST uçları eklenir. Sunucu `PREVIEW → TRANSITION → INPUT → REVEAL` geçişlerini mutlak zamanlarla yönetir; ilk durumda bellek içi depo kullanılır.
- **Kabul:** API ile 5 raund bitirilebilir; hedef `INPUT` aşamasında yanıtta bulunmaz; geç veya farklı içerikli ikinci tahmin reddedilir; skor yalnız sunucuda hesaplanır.
- **Sınır:** Çok oyunculu akış yoktur.
- **Durum:** Tamamlandı. REST uçları, bellek içi maç akışı ve kararlı hata kodları eklendi; beş raund, zaman sınırları, hedef gizleme ve tekrar tahmin davranışı test edildi. Sonuçtan sonraki raund veya finale geçiş daha sonra `Devam` komutuna bağlandı.
- **Mimari düzenleme:** Ortak zaman/raund akışı `GameDefinition` üzerinden oyuna özel kurallardan ayrıldı; `GameConfig` yalnız ortak süreleri, `ColorGameConfig` renk puan ayarını taşır. API sonucu `gameData` içinde oyuna özel veri sunar.

### 5. Tek kişilik oynanabilir ekran

- **Çıktı:** Hedef gösterimi, gizleme, renk seçici, sayaç, sonuç ve 5 raund sonu ekranı bağlanır.
- **Kabul:** Kullanıcı tarayıcıda tam oyunu oynar; hedef tahmin sırasında görünmez; mobil ekranda renk seçilebilir.
- **Sınır:** İlk sürümde sade bir renk seçici yeterlidir; kullanım testi gerekirse özel seçiciye geçilir.
- **Durum:** Tamamlandı. Project ana ekranından Color oyununa girilir; Game1 ve Game2 yer tutucudur. React ekranında önizleme, tahmin, sonuç, `Devam` ile raund geçişi ve beş raund finali çalışır. Mobilde yerel renk seçici kullanılır. Sayaç sunucu zamanına göre gösterilir; hedef renk tahmin aşamasında ekranda bulunmaz.

### 6. Tek kişilik toparlanma ve kullanılabilirlik

- **Çıktı:** Sayfa yenilemede sunucu snapshot'ından doğru aşamaya dönme, tekrar oynama, klavye kullanımı ve renk dışı durum açıklamaları eklenir.
- **Kabul:** Yenileme süreleri sıfırlamaz; bitmiş oyun yeniden açılamaz; temel akış klavyeyle tamamlanır.
- **Sınır:** Geçmiş oyunlar listelenmez.
- **Durum:** Tamamlandı. Aktif tek kişilik maç kimliği tarayıcı oturumunda tutulur; yenilemede sunucudan snapshot okunur ve süreler sıfırlanmadan doğru aşama açılır. Tamamlanan veya kaybolan maç kimliği temizlenir. Yeniden oynama yeni maç açar. HEX kodu klavyeyle girilebilir; aşama metinleri ve odak geçişleri eklendi.

### 7. Anonim oyuncu ve lobi REST akışı

- **Çıktı:** Anonim oturum cookie'si, takma ad, 6 karakterli lobi kodu, oluşturma/katılma/ayrılma ve ev sahibi yetkisi eklenir.
- **Kabul:** İki ayrı tarayıcı aynı lobiye katılır; yetkisiz başlatma, hatalı kod, dolu lobi ve yinelenen ad doğru hata verir.
- **Sınır:** Lobi değişiklikleri bu aşamada yeniden okuma ile görülebilir.
- **Durum:** Tamamlandı. Anonim `HttpOnly` cookie, bellek içi lobi servisi ve REST uçları eklendi. Color ekranından lobi kurulup kodla katılınabilir; üye listesi düğmeyle yenilenir. Kapasite, ad çakışması, üyelik ve ev sahibi yetkisi ile eşzamanlı son koltuk test edildi. Çok oyunculu maç başlatma 9. aşamada açılacak.

### 8. Canlı lobi

- **Çıktı:** WebSocket/STOMP ile üye listesi ve lobi olayları anlık yayınlanır; abonelikte lobi üyeliği doğrulanır.
- **Kabul:** Katılma ve ayrılma diğer ekranda yenilemesiz görünür; bağlantı sonrası snapshot okunur.
- **Sınır:** Chat ve seyirci yoktur.
- **Durum:** Tamamlandı. `/api/v1/ws` bağlantısı anonim oturum cookie'sini doğrular; `/topic/lobbies/{code}` aboneliği yalnız lobi üyelerine açılır. Katılma ve ayrılma olayları sıra numarasıyla yayınlanır. İstemci olayda ve yeniden bağlanmada REST snapshot'ını okuyarak üye listesini günceller. WebSocket entegrasyon testi iki oyuncunun katılma ve ayrılma olaylarını doğrular.

### 9. Eşzamanlı çok oyunculu oyun

- **Çıktı:** Ev sahibi oyunu başlatır; sunucu tüm oyunculara aynı hedefi, aşama zamanlarını ve sonucu uygular. Oyuncuların hepsi tahmin yapınca , raund sonunda tüm oyuncuların yalnızca o raundda aldığı puanlar gözükür. Oyuncular devam'a basınca hangi oyuncunun hazır olduğu ibaresi ekranda gözükür. Tüm oyuncular devam dediğinde sıradaki raunda geçilir. En az bir oyuncu devam dedikten sonra diğer oyuncular hazır vermese bile sıradaki raund 15 saniye sonra başlar.
- **Kabul:** İki oyuncu 5 raundu birlikte tamamlar; puanlar ve final sıralaması aynı görünür; geç/çift tahmin veya aynı anda iki başlatma sonucu bozmaz.
- **Sınır:** Tek lobide tek aktif maç vardır.
- **Durum:** Tamamlandı. Ev sahibi en az iki oyuncuyla maçı başlatır; lobi ve maç durumu bellekte atomik tutulur. Ortak hedef ve sunucu saatli aşamalar, oyuncu başına tek tahmin, tüm tahminler gelince veya süre dolunca açılan raund puanları, hazır listesi, ilk hazır oyuncudan itibaren 15 saniyelik üst sınır ve beş raundluk final sıralaması eklendi. İki oyunculu beş raund, geç/çift tahmin, hazır zamanlaması ve eşzamanlı başlatma test edildi.

### 10. Kopma, süre sonu ve rövanş

- **Çıktı:** Kısa bağlantı kopmasından geri dönüş, kayıtlı renk seçimi bulunmayan eksik tahmine 0 puan, boş lobi temizliği, ev sahibi ayrılma kuralı ve aynı lobide rövanş eklenir.
- **Kabul:** Sayfa yenileme oyuncuyu aynı maça döndürür; kopan oyuncu raundu durdurmaz; biten lobide yeni maç başlatılabilir.
- **Sınır:** Bu aşamada sunucu yeniden başlarsa aktif maç kaybolabilir.
- **Durum:** Tamamlandı. Sayfa yenileme anonim oturum ve lobi koduyla güncel maçı geri yükler; bağlantı kesilmesi sunucu saatini durdurmaz. Gönderilmeyen tahminde son kaydedilen renk puanlanır, kayıtlı renk de yoksa 0 puan verilir. Son üye ayrılınca lobi hemen, süresi dolan lobiler düzenli olarak temizlenir; ev sahibi ayrılırsa sıradaki üyeye yetki geçer. Bitmiş lobide ev sahibi, en az iki üye varsa aynı kodla yeni maç başlatabilir. Rövanş, boş lobi ve eksik tahmin akışları test edildi.

### 11. Kalıcı kayıt — V1 kapsamından çıkarıldı

- **Karar:** İlk sürüm maçları, skorları, lobileri veya anonim oturumları kalıcı olarak saklamayacak. Mevcut PostgreSQL/Flyway şeması ve JPA Entity sınıfları oyun akışına bağlanmayacak; ileride kayıt ihtiyacı doğarsa yeniden değerlendirilecek.
- **Sonuç:** Backend yeniden başlatılırsa bellekteki aktif ve bitmiş maçlar ile lobiler kaybolur. Sayfa yenilemeyle maça dönüş yalnız backend çalışmaya devam ederken geçerlidir.
- **Durum:** Tamamlandı.

### 12. V1 yayına hazırlık

- **Çıktı:** Hız sınırı, idempotency, SQL injection ve XSS denetimi, CSRF/origin koruması, girdi sınırları, lobi abonelik testi, temel metrik/log, iki tarayıcılı uçtan uca test ve dağıtım adımları tamamlanır. Örnek dağıtım mevcut PostgreSQL açılış bağımlılığını korur; maç verisi kalıcı olarak saklanmaz.
- **Kabul:** Tek kişilik ve iki oyunculu akış mobil/masaüstünde geçer; build/test/derleme başarılıdır; health ve hata bilgileri görülebilir.
- **Sınır:** Tek backend örneğiyle yayınlanır; ikinci oyun ve yatay ölçekleme ayrı iş olur.
- **Durum:** Tamamlandı. REST değişiklik çağrılarında kaynak denetimi, IP başına okuma/yazma/oluşturma sınırları, 4 KiB JSON gövde sınırı ve oturumlu oluşturma tekrarlarında `Idempotency-Key` eklendi. Kullanıcı girdisiyle SQL çalıştıran bir yol bulunmuyor; takma ad React tarafından metin olarak gösteriliyor ve örnek HTTPS proxy yapılandırması script CSP'si uyguluyor. Üye olmayan WebSocket aboneliği entegrasyon testi, mobil tek kişilik akış ve mobil/masaüstü iki tarayıcıyla beş raund, yenileme ve rövanş testleri geçti. Actuator metrikleri ayrı yerel portta, dağıtım adımları `deploy/` altında. Canlı sunucuya dağıtım yapılmadı.

## V1 tamamlanma ölçütü

Hesapsız tek kişilik oyun ve kodla çok oyunculu maç çalışır; hedef tahmin aşamasında gizlidir; tüm süreler ve skorlar backend otoritesindedir; kısa kopmadan dönüş ve aynı lobide rövanş vardır. Maçlar yalnız çalışan backend belleğinde tutulur; sunucu yeniden başladığında oyunlar sıfırlanır. Yeni bir oyun eklenirken renk kuralları değiştirilmeden ortak lobi akışı kullanılabilir.
