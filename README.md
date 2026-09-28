# Project

Şu anda Spring Boot backend, React + TypeScript + Vite frontend ve PostgreSQL/Flyway temeli hazırdır. Tek kişilik renk hafıza oyunu tarayıcıda oynanabilir. Color ekranından çok oyunculu lobi kurulabilir veya altı karakterli kodla katılınıp birlikte maç oynanabilir.

Aşama 3 renk motoru backend içinde hazırdır: hedef üretimi, `#RRGGBB` doğrulaması, sRGB→Lab dönüşümü ve CIEDE2000 puanlaması. Aşama 4'te tek kişilik oyun API'sine bağlandı. Başlangıç `scale=20.0` değeri kullanıcı testiyle kalibre edilecek varsayımdır.

Canlı ortam için derleme, aynı alan adından API/WebSocket erişimi, ortam değişkenleri ve doğrulama adımları [dağıtım kılavuzunda](deploy/README.md) bulunur. İlk sürüm maç geçmişi tutmaz; sunucu yeniden başlayınca bellekteki oyunlar sıfırlanır.

## Gereksinimler

- Java 21 JDK
- Node.js 20.19+ veya 22.12+
- Docker Desktop

Gradle kurulumu gerekmez; `backend` klasöründe Gradle Wrapper bulunur.

## Yerelde çalıştırma

Proje kökünde PostgreSQL'i başlatın:

```powershell
docker compose up -d postgres
```

İkinci terminalde backend'i başlatın:

```powershell
cd backend
.\gradlew.bat bootRun
```

Üçüncü terminalde frontend'i başlatın:

```powershell
cd frontend
npm ci
npm run dev
```

Backend ve frontend terminallerini açık tutun; bu işlemler durursa site açılmaz. Geliştirme adresi `http://localhost:5173`'tür; `8080` yalnız API içindir.

`http://localhost:5173` adresini açın. **Project** ana ekranında **Color** oyununu seçince adres `/color`, çok oyunculu lobiye geçince `/color/lobby` olur; tarayıcının geri/ileri düğmeleri bu ekranlar arasında çalışır. **Oyuna başla** düğmesine basın. Game1 ve Game2 şimdilik yer tutucudur. Hedef renk 3 saniye gösterilir; kaybolunca kısa bir animasyonla tahmin ekranı açılır. Rengi hemen seçebilirsiniz, tahmin gönderimi sunucudaki kısa geçiş süresi bitince etkinleşir. Renk seçiciyle veya HEX kodu yazarak tahmininizi gönderince sonuç görünür; **Devam** düğmesine basana kadar aynı raundda kalır. Beşinci raunddan sonra **Devam** final ekranını açar. Aktif maç sırasında sayfayı yenilerseniz sunucudaki güncel aşama ve süreyle oyuna dönersiniz. **Yeniden oyna** yeni maç başlatır. Backend health endpoint'i doğrudan `http://localhost:8080/api/v1/health` adresindedir ve `{"status":"UP"}` döndürür. Vite geliştirme sunucusu `/api` isteklerini 8080 portuna yönlendirir.

Backend şu anda başlangıçta Flyway V1 ve V2 migration'larını uygular ve JPA eşlemesini `validate` modunda kontrol eder. Bu nedenle veritabanı çalışmadan backend başlamaz. Oyun verisi bu tablolara okunup yazılmaz; canlı ortamda bu açılış bağımlılığının tutulması veya kaldırılması dağıtım sırasında kararlaştırılacaktır. Yerel veritabanı `127.0.0.1:5432` adresinde `color_memory` adıyla çalışır. Varsayılan parola yalnız yerel geliştirme içindir: `local_dev_only`. Parola değiştirilirse Compose için `POSTGRES_PASSWORD`, backend için aynı değerde `DB_PASSWORD` ayarlanmalıdır. Bağlantı adresi ve kullanıcı adı `DB_URL` ve `DB_USERNAME` ile değiştirilebilir.

## Tek kişilik oyun API'si

Tüm yollar `http://localhost:8080/api/v1` altındadır:

| İşlem | Yöntem ve yol |
| --- | --- |
| Oyunu başlat | `POST /single-games` |
| Anlık durumu oku | `GET /games/{id}` |
| Raund tahmini gönder | `PUT /games/{id}/rounds/{roundNumber}/submission` |
| Seçili rengi taslak olarak kaydet | `PUT /games/{id}/rounds/{roundNumber}/draft` |
| Sonuçtan devam et | `POST /games/{id}/continue` |

Başlatma `201 Created` döndürür; `Location` başlığı oyun durumunun yoludur. İsteğe bağlı başlatma gövdesi `{"gameType":"COLOR_GUESS"}` biçimindedir; boş gövde renk oyununu başlatır. Tahmin gövdesi `{"guess":{"color":"#4285D0"}}` biçimindedir; önceki `{"guessColor":"#4285D0"}` biçimi de kabul edilir. Taslak gövdesi `{"guess":{"color":"#4285D0"},"revision":1}` biçimindedir. Sunucu saati ve süreli aşamanın bitişi `serverTime` ile `phaseEndsAt` alanlarında UTC olarak döner. Tek kişilik aşamalar `PREVIEW` (3 sn), `TRANSITION` (750 ms), `INPUT` (10 sn), ardından `REVEAL` şeklindedir. Geçerli tahmin gönderildiğinde `REVEAL` hemen başlar; süre dolduğunda gönderilmemiş tahmin yerine son kaydedilen renk puanlanır. `REVEAL` süresizdir ve `phaseEndsAt=null` döner. Sonraki raund veya final yalnız `POST /games/{id}/continue` ile açılır. İstemci durum almak için `GET` ile yeniden okuyabilir; süreyi belirleyen istemci değil sunucudur.

`GET /api/v1/session` anonim cookie'yi başlatır. `POST /single-games` ve `POST /lobbies` isteklerinde isteğe bağlı `Idempotency-Key` (8–128 harf, rakam, `_` veya `-`) kullanılabilir. Aynı oturum, anahtar ve gövdeyle 10 dakika içindeki tekrar aynı oyun veya lobiyi döndürür; aynı anahtar farklı gövdede `409` verir. Tarayıcı arayüzü oluşturma öncesi oturumu açar ve bağlantı hatasında aynı anahtarı yeniden kullanır.

Ortak maç alanlarından ayrı `gameData` alanı renk oyununda `targetColor` ve sonuçta `guessedColor`/`colorDistance` taşır. `INPUT` sırasında mevcut raundun `gameData` alanı ve puanı yanıtta bulunmaz; `yourDraft` yalnız oyuncunun kendi son kaydedilen rengidir. Tahmin yalnız bu aşamada kabul edilir; aynı renk tekrar gönderilirse mevcut durum döner, farklı ikinci tahmin `409` verir. Geç tahmin de `409` verir. Hatalar `code` alanlı Problem Details gövdesiyle döner. Beş raund bitince `status=FINISHED`, `phase=COMPLETED` olur. Hiç taslak kaydedilemeyen ve gönderilmeyen raund 0 puandır. Bu aşamadaki maçlar yalnız bellektedir; backend yeniden başlarsa kaybolur.

Aktif maçın kimliği tarayıcının `sessionStorage` alanında tutulur. Sayfa yenilenince `GET /games/{id}` ile sunucunun güncel durumu alınır; tarayıcı saatleri yeniden başlatmaz. Sunucu yeniden başlatılıp maç kaybolursa kimlik temizlenir ve yeni oyun başlatılabilir. Tamamlanan maç kimliği de temizlenir; sonuç ekranındaki **Yeniden oyna** yeni bir maç oluşturur. İki saat kullanılmayan tek kişilik maçlar bellekten temizlenir. İlk sürüm maç geçmişi tutmaz; maç ve skorlar PostgreSQL'e yazılmaz.

**Project** ile ana sayfaya dönüldüğünde aktif tek kişilik oyun kaydı temizlenir. Color’a yeniden girildiğinde eski raund açılmaz; yeni oyun başlangıç ekranı gösterilir. Aktif çok oyunculu lobiden ana sayfaya dönülürse lobi üyeliğinden de ayrılınır. `/color` sayfasında yenileme ise mevcut oyunu kaldığı yerden yüklemeye devam eder.

## Anonim lobi API'si

Color ekranındaki **Çok oyunculu lobi** bağlantısından takma adla lobi oluşturabilir veya kodla katılabilirsiniz. Katılımcı listesi WebSocket üzerinden katılma/ayrılma olaylarıyla otomatik güncellenir; **Listeyi yenile** düğmesi de kullanılabilir. Her tarayıcıya ilk oyun/lobi isteğinde `HttpOnly`, `SameSite=Lax` anonim oturum cookie'si verilir; hesap ve şifre yoktur.

| İşlem | Yöntem ve yol | Gövde |
| --- | --- | --- |
| Lobi oluştur | `POST /lobbies` | `{"displayName":"Ada","maxPlayers":4}` |
| Kodla katıl | `POST /lobbies/{code}/join` | `{"displayName":"Bora"}` |
| Üye listesini oku | `GET /lobbies/{code}` | — |
| Lobiden ayrıl | `POST /lobbies/{code}/leave` | — |
| Aynı lobide rövanş başlat | `POST /lobbies/{code}/rematch` | — |

Kod 6 karakterdir; lobi kapasitesi 2–8 oyuncudur. Adlar aynı lobide büyük/küçük harf duyarsız benzersizdir. Yalnız üyeler listeyi görebilir. Ev sahibi ayrılırsa sıradaki oyuncu ev sahibi olur. En az iki oyuncu katıldığında ev sahibi maçı başlatabilir. Aktif maç sırasında yeni katılım kapalıdır; ayrılan oyuncu kalanları bekletmez. Biten maça yeni oyuncu katılabilir; eski final sonucu yalnız o maça katılanlara gösterilir. Son üye ayrılınca lobi silinir; iki saatlik süresi dolan lobiler dakikada bir temizlenir. Anonim oturum, lobi ve maç şu anda bellektedir; backend yeniden başlarsa kaybolurlar.

## Çok oyunculu maç

İki ayrı tarayıcıda aynı lobiye katılın; ev sahibi **Oyunu başlat** düğmesine basar. Her oyuncu aynı hedefi üç saniye görür. Hedef gizlendikten sonra tahmin süresi 10 saniyedir. Herkes tahminini gönderdiğinde veya süre dolduğunda o raundun oyuncu puanları açılır. **Devam** düğmesine basan oyuncular hazır olarak görünür. Herkes hazırsa sonraki raund hemen, aksi halde ilk hazır oyuncudan 15 saniye sonra başlar. Beşinci raunddan sonra toplam puana göre final sıralaması açılır.

Maç sırasında **Lobiden ayrıl** kullanılabilir. Final sıralamasında **Lobiye dön** ve **Lobiden ayrıl** seçenekleri vardır. En az iki üye kalmışsa ev sahibi finalden veya lobiden **Rövanş başlat** ile aynı kodda yeni bir maç açabilir. Diğer üyeler yeni maça otomatik geçer. Sol üstteki **Project** bağlantısı ana sayfayı açar. Sayfa yenilemek aynı anonim oturumla mevcut maça döndürür; geçici bağlantı kopması raund saatini durdurmaz. Lobiden açıkça ayrılmak üyeliği hemen siler.

| İşlem | Yöntem ve yol |
| --- | --- |
| Ev sahibinin maçı başlatması | `POST /lobbies/{code}/start` |
| Ev sahibinin rövanşı başlatması | `POST /lobbies/{code}/rematch` |
| Üyenin maç durumunu okuması | `GET /lobbies/{code}/game` |
| Raund tahmini | `PUT /lobbies/{code}/game/rounds/{roundNumber}/submission` |
| Seçili rengi taslak olarak kaydet | `PUT /lobbies/{code}/game/rounds/{roundNumber}/draft` |
| Sonraki raunda hazır olma | `POST /lobbies/{code}/game/ready` |

Tahmin gövdesi `{"guess":{"color":"#4285D0"}}` biçimindedir. Taslak gövdesi aynı `guess` alanına ek olarak artan bir `revision` taşır. Yanıtta `serverTime`, `phaseEndsAt`, oyuncunun kendi `yourDraft` rengi, raund puanları ve oyuncuların hazır durumu bulunur. Güncel hedef `INPUT` sırasında gönderilmez. Süre sonunda açık tahmin yoksa son kaydedilen taslak puanlanır; taslak da yoksa 0 puan verilir. Çift tahminde farklı renk `409` döndürür. Aynı lobide ikinci bir aktif maç başlatılamaz.

Canlı lobi bağlantısı `/api/v1/ws` yolunda STOMP kullanır. Üye istemciler `/topic/lobbies/{code}` adresine abone olur; sunucu mevcut anonim oturumu ve lobi üyeliğini doğrular. Bağlantı yeniden kurulunca ekran sunucudan güncel lobi durumunu tekrar okur. Vite `/api` WebSocket bağlantılarını da backend'e yönlendirir.

PowerShell ile deneme:

```powershell
$game = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/single-games' -ContentType 'application/json' -Body '{}'
$game.id
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/games/$($game.id)"
# phase INPUT olduğunda:
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/v1/games/$($game.id)/rounds/1/submission" -ContentType 'application/json' -Body '{"guess":{"color":"#4285D0"}}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/games/$($game.id)/continue"
```

## Derleme

PostgreSQL çalışırken backend için:

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat build
```

Frontend için:

```powershell
cd frontend
npm run build
npm run test:e2e
```

Tarayıcı testi yerel Google Chrome ve çalışan PostgreSQL gerektirir; backend ile Vite kapalıysa test komutu onları başlatır. API hız sınırı ve girdi korumaları ile canlı metrik erişimi [dağıtım kılavuzunda](deploy/README.md) açıklanır.

VS Code'da `jakarta.persistence`, `org.springframework.messaging` veya `org.springframework.web.socket` importları kırmızı görünürse komut paletinden **Java: Update Project** çalıştırın. Sorun sürerse **Java: Clean Java Language Server Workspace** komutuyla Java çalışma alanını yeniden oluşturun; ardından `backend/build.gradle` projesinin Gradle olarak içe aktarılmasını bekleyin.

Yol haritası [roadmap.md](roadmap.md), teknik kararlar [architecture.md](architecture.md), veritabanı şeması [database-design.md](database-design.md) dosyasındadır.
