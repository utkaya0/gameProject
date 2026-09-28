# Tek sunucuda V1 yayını

Bu örnek tek backend süreci ve aynı alan adında frontend, REST ve WebSocket bağlantısı içindir. Oyun ve lobi verileri bellektedir; süreç yeniden başlayınca aktif maçlar ve sonuçlar kaybolur.

1. Sunucuda Java 21, Node.js/npm, PostgreSQL ve Caddy kurun. PostgreSQL için bir veritabanı ve kullanıcı oluşturun. Backend şu anda Flyway ve JPA doğrulaması için açılışta PostgreSQL'e bağlanır; oyun verisi tablolara yazılmaz.
2. `frontend` dizininde `npm ci` ve `npm run build` çalıştırın. Oluşan `frontend/dist` dizinini Caddy'nin okuyacağı yere kopyalayın.
3. `backend` dizininde `./gradlew bootJar` (Windows: `.\gradlew.bat bootJar`) çalıştırın. Backend için aşağıdaki ortam değişkenlerini ayarlayın ve `java -jar backend/build/libs/backend-0.0.1-SNAPSHOT.jar` ile başlatın:

   ```text
   SERVER_ADDRESS=127.0.0.1
   SERVER_PORT=8080
   APP_ALLOWED_ORIGINS=https://example.com
   APP_TRUST_LOOPBACK_PROXY=true
   DB_URL=jdbc:postgresql://127.0.0.1:5432/color_memory
   DB_USERNAME=color_memory
   DB_PASSWORD=<güçlü parola>
   ```

4. [Caddyfile.example](Caddyfile.example) dosyasındaki `example.com` alan adını ve `root` dizinini gerçek değerlerle değiştirin. Caddy'yi bu dosyayla çalıştırın. `/api/*` yolu backend'e aktarılır; `/api/v1/ws` WebSocket bağlantısı aynı yoldan geçer. Uygulamanın `/color` ve `/color/lobby` adresleri frontend'in `index.html` dosyasına yönlenir.
5. `https://example.com/api/v1/health` sonucunun `{"status":"UP"}` olduğunu, `/color` ve `/color/lobby` sayfalarının açıldığını ve iki farklı tarayıcıdan aynı maçın oynanabildiğini kontrol edin. Backend metrikleri yalnız sunucuda `http://127.0.0.1:8081/actuator/metrics` adresindedir; bu portu internete açmayın.

`APP_ALLOWED_ORIGINS` tam tarayıcı kaynağıdır (`https://` ve alan adı); hem REST kaynak denetiminde hem WebSocket el sıkışmasında kullanılır. Backend yalnız döngü adresinde dinlerken `APP_TRUST_LOOPBACK_PROXY=true`, proxy'nin ilettiği istemci IP'sini hız sınırı için ve `X-Forwarded-Proto: https` bilgisini güvenli cookie için kabul eder. Backend başka istemcilere doğrudan açıksa bu ayarı kullanmayın. Varsayılan hız sınırı aynı istemci IP'si için dakikada 20 oyun/lobi oluşturma, 180 diğer değişiklik isteği, 60 anonim oturum açma ve 1200 diğer okuma isteğidir; JSON gövde sınırı 4 KiB'dir. Health isteği hız sınırına dahil değildir.

Yerelde otomatik kontrol için PostgreSQL, backend ve frontend çalışırken `frontend` dizininde `npm run test:e2e` komutu iki tarayıcı bağlamını kullanır. Testler yerel Google Chrome kurulumu gerektirir. Backend testleri `backend` dizininde `./gradlew test` ile çalışır.
