# AndroidMouse Alfa

**AndroidMouse ver. Alfa ** to projekt typu open-source, który przekształca Twój
telefon z systemem Android w bezprzewodową mysz Bluetooth dla komputera.

Aplikacja składa się z dwóch głównych komponentów:

1. **Aplikacji na Androida** – działającej jako klient, która przechwytuje gesty dotykowe i kliknięcia.
2. **Serwera w Pythonie** – działającego na komputerze, który odbiera sygnały z telefonu i tłumaczy je na rzeczywiste ruchy i kliknięcia kursora myszy.

## Spis treści

1. [Funkcjonalności](#funkcjonalności)
2. [Wymagania Systemowe](#wymagania-systemowe)
3. [Instrukcja Uruchomienia](#instrukcja-uruchomienia)
    - [Krok 1: Konfiguracja serwera na komputerze](#krok-1-konfiguracja-serwera-na-komputerze)
    - [Krok 2: Instalacja i uruchomienie aplikacji na Androidzie](#krok-2-instalacja-i-uruchomienie-aplikacji-na-androidzie)
4. [Jak to działa?](#jak-to-działa)
5. [Znane Problemy i Rozwój](#znane-problemy-i-rozwój)

---

## Funkcjonalności

- **Ruch kursora**: Przesuwaj palcem po wyznaczonym obszarze (touchpadzie) na ekranie telefonu, aby poruszać kursorem na komputerze.
- **Kliknięcia**: Dedykowane przyciski dla lewego i prawego kliknięcia myszy.
- **Łączność Bluetooth**: Komunikacja oparta na standardowym profilu portu szeregowego (SPP).
- **Wybór urządzenia**: Aplikacja pozwala na łatwy wybór komputera z listy sparowanych urządzeń.

---

## Wymagania Systemowe

### Aplikacja Android
- Minimalna wersja SDK: **24** (Android 7.0 Nougat)
- Docelowa wersja SDK: **34** (Android 14)
- Wersja Android Studio: Kompilowano i testowano na **Android Studio Narwhal 4 | 2025.1.4**
- Urządzenie z systemem Android i modułem Bluetooth.

### Serwer na Komputerze
- Wersja Pythona: **Python 3.11** (konieczna)
- System operacyjny: **Windows**
- Komputer z modułem Bluetooth.

---

## Instrukcja Uruchomienia

Aby korzystać z aplikacji, musisz najpierw skonfigurować serwer na komputerze, a następnie połączyć się z nim za pomocą aplikacji na Androida.

### Krok 1: Konfiguracja serwera na komputerze (PC / Linux)

1. **Sparuj urządzenia**

   Najpierw sparuj swój telefon z komputerem w systemowych ustawieniach Bluetooth.

2. **Sklonuj repozytorium**

3.  **Przejdź do folderu serwera:**  
    Wszystkie pliki serwera znajdują się w folderze `AndroidMouseAlfa - phyton server`.


----------

### Krok 2: Instalacja i uruchomienie aplikacji na Androidzie

1.  Wersja jeszcze nie jest gotową aplikacją .apk. Trzeba sammu skomipolwać kod.

2.  Uruchom aplikację i wybierz komputer z listy sparowanych urządzeń Bluetooth.

3.  Połącz się i rozpocznij korzystanie z telefonu jako myszy!


----------

## Jak to działa?

Aplikacja wysyła dane o dotyku, przesunięciach i kliknięciach poprzez Bluetooth SPP do serwera Pythonowego, który interpretuje je jako ruchy kursora lub akcje myszy w systemie operacyjnym.

----------

## Znane Problemy i Rozwój

-   Planowane jest korzystanie z żyroskopu w telefonie.
-   Program posiada prosty interfejs graficzny z systmem log.
-   Planowane jest  dodanie przycisków sumulujących kółko myszy orac pasek przewijania.
-   Planowane jest dodanie funkcji przewijania dwoma palcami oraz regulacji czułości kursora.
-   Planowane jest zintegrowanie serwera do paczki .apk