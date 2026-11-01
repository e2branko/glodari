# Noćni slušalac glodara (Android)

Foreground servis koji preko mikrofona (48 kHz, bez obrade zvuka) prati opseg 3–23 kHz,
prilagođava se pozadinskoj buci i beleži cijukanje / šuškanje / grebanje.
Radi i sa ugašenim ekranom.

## Kako da napraviš APK
1. Instaliraj Android Studio (Koala ili novije), File → Open → izaberi ovaj folder.
2. Sačekaj Gradle sync (treba internet; skida Gradle 8.7 i zavisnosti).
3. Na telefonu: Podešavanja → O telefonu → 7x tapni "MIUI verzija" → uključi
   Opcije za programere → USB debugging. Poveži kablom.
4. Klikni Run ▶ (ili Build → Build APK(s), pa prebaci APK na telefon).

## Obavezna podešavanja na Xiaomi (MIUI) da servis ne bude ubijen preko noći
- Podešavanja → Aplikacije → Upravljanje aplikacijama → Noćni slušalac →
  Štednja baterije → **Bez ograničenja**
- Isto mesto → **Automatsko pokretanje** uključeno
- U meniju nedavnih aplikacija zaključaj aplikaciju (dugi pritisak na karticu → katanac)
- Dozvoli obaveštenja i mikrofon

## Napomene
- Telefon stavi na punjač, blizu mesta gde sumnjaš da su glodari.
- Mikrofon telefona slabo hvata ultrazvuk iznad ~20 kHz; najbolje hvata ono što i čuješ.
- Prvih par sekundi je kalibracija pozadinske buke.
- Dnevnik se čuva u aplikaciji (poslednjih 2000 događaja).

## Kompajliranje online (bez Android Studija)
Folder `.github/workflows/build.yml` već postoji. Okači projekat na GitHub (novi repozitorijum),
pa u tabu **Actions** otvori "Build APK" (ili klikni Run workflow). Kad završi,
na dnu stranice preuzmi **GlodarSlusalac-debug-apk** (zip sa app-debug.apk).
