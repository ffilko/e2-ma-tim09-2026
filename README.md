# Mobilne aplikacije - Slagalica - Tim09

## 1. Podešavanje okruženja
Za uspešno pokretanje projekta koristi se **Android Studio**. Pre pokretanja, proveriti da li su instalirani sledeći paketi:
* Android SDK Build-Tools
* Android SDK Command-line Tools
* Android SDK platform-tools
* Android Emulator
## 2. Otvaranje projekta
Za importovanje postojećeg projekta izabrati iz menija File > New > Import Project.  
Za kloniranje projekta sa Git-a, odaberite File > New > Project from Version Control i unesite URL do repozitorijuma.

## 3. Pokretanje aplikacije
### 3.1 Pokretanje aplikacije na fizičkom uređaju
Da bi se aplikacija pokrenula na fizičkom uređaju potrebno je omogućiti *developer options*.
Najčešći koraci su: 
1. Settings > About Phone > Build Number 
Kada pronađete Build Number potrebno je da 7 puta kliknete na njega i nakon toga će se 
prikazati poruka da je developer options omogućen. 
2. Settings > System > Advanced > Developer Options > USB debugging 
Prevući dugme USB debugging, tako da bude omogućeno. 
3. Povezivanje uređaja sa računarom uz pomoć USB kabla.

Povezani uređaj treba da je dostupan u okviru AS. Izaberite uređaj i kliknite dugme za pokretanje **Run**.

### 3.2  Pokretanje aplikacije na virtuelnom uređaju
Odabirom stavke iz menija **Tools > AVD Manager** otvara se prozor za kreiranje novog AVD-a. Klikom na dugme + Create Virtual Device otvara se prozor za konfigurisanje hardverskih podešavanja. Posle odabira odgovarajućih hardverskih podešavanja i klikom na dugme Next prikazuje se prozor System Image, gde birate Android verziju.
Pokretanje ovog emulatora se vrši odabirom uređaja iz AVD Manager-a i klikom na dugme za pokretanje **Run**.

