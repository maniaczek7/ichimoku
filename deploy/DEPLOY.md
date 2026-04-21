# Deployment — Oracle Cloud Always Free

## Krok 1: Zaloz konto Oracle Cloud

1. Wejdz na https://cloud.oracle.com i kliknij **Sign Up**
2. Podaj email, imie, region (**EU Frankfurt** — `eu-frankfurt-1`)
3. Podaj karte platnicza (nie pobiera nic na Always Free)
4. Po weryfikacji zaloguj sie do konsoli

## Krok 2: Stworz VM (Always Free)

### 2.1 Otworz kreator

1. Po zalogowaniu jestes na **Dashboard** Oracle Cloud
2. W lewym gornym rogu kliknij **hamburger menu** (☰) — trzy kreski
3. Idz do: **Compute** → **Instances**
4. Kliknij niebieski przycisk **Create Instance**

### 2.2 Nazwa i compartment

- **Name**: wpisz `ichimoku`
- **Create in compartment**: zostaw domyslny (root) — nic nie zmieniaj
- **Placement**: zostaw domyslny (Availability Domain — AD-1)

### 2.3 Image and shape (System operacyjny + maszyna)

To jest najwazniejsza sekcja — tutaj wybierasz darmowa maszyne ARM.

**Image (system):**
- Domyslnie jest **Oracle Linux 9** — zostaw to, nie zmieniaj
- Jezeli chcesz zmienic: kliknij **Change image** → wybierz **Oracle Linux** → wersja **9**

**Shape (typ maszyny):**
- Kliknij **Change shape**
- W oknie ktore sie otworzy:
  1. **Shape series**: zaznacz **Ampere** (ARM-based processor)
     - NIE wybieraj AMD ani Intel — te NIE sa w Always Free
  2. Z listy ponizej zaznacz **VM.Standard.A1.Flex**
     - Obok nazwy powinien byc napis **Always Free Eligible** ✓
  3. Pojawia sie suwak do konfiguracji:
     - **Number of OCPUs**: ustaw na **2** (mozesz dac do 4, ale 2 wystarczy)
     - **Amount of memory (GB)**: ustaw na **12** (mozesz dac do 24, ale 12 wystarczy)
     - Na dole powinno pisac: **Always Free Eligible** — upewnij sie ze tak jest
  4. Kliknij **Select shape**

> **UWAGA**: Jesli VM.Standard.A1.Flex jest niedostepny ("Out of capacity") — to jest
> czesty problem na popularnych regionach. Sprobuj:
> - Inny **Availability Domain** (AD-2, AD-3) jesli jest dostepny
> - Poczekaj kilka godzin i sprobuj ponownie (pojemnosc sie zwalnia)
> - Zmien region na mniej popularny (np. Amsterdam zamiast Frankfurt)

### 2.4 Networking (Siec)

Ta sekcja jest pod **Image and shape**. Kliknij **Edit** jesli jest zwinieta.

**Jezeli to Twoja pierwsza VM (nie masz jeszcze sieci):**
1. Zaznacz: **Create new virtual cloud network**
   - Name: `ichimoku-vcn` (lub zostaw domyslna)
2. Zaznacz: **Create new public subnet**
   - Name: zostaw domyslna
3. **Public IPv4 address**: zaznacz **Assign a public IPv4 address**
   - To jest KRYTYCZNE — bez tego nie bedziesz miec publicznego IP

**Jezeli juz masz VCN z poprzednich prob:**
1. Zaznacz: **Select existing virtual cloud network**
2. Wybierz swoj VCN z listy
3. Wybierz **public subnet**
4. **Public IPv4 address**: zaznacz **Assign a public IPv4 address**

### 2.5 SSH Keys (Klucz dostepu)

Tym kluczem bedziesz sie laczyc z serwerem przez SSH.

**Opcja A — Oracle generuje klucz (najlatwiej):**
1. Zaznacz **Generate a key pair**
2. Kliknij **Save private key** — pobierze sie plik np. `ssh-key-2026-03-22.key`
3. **ZAPISZ TEN PLIK W BEZPIECZNYM MIEJSCU** — nie zgub go, nie da sie go pobrac ponownie
4. (Opcjonalnie) kliknij **Save public key** — tez zapisz

**Opcja B — Uzyj swojego klucza (jesli juz masz):**
1. Zaznacz **Upload public key files** lub **Paste public keys**
2. Wklej zawartosc pliku `~/.ssh/id_rsa.pub` (lub `id_ed25519.pub`)

### 2.6 Boot volume (Dysk)

- Zostaw **wszystko domyslnie** — nic nie zmieniaj
- Domyslnie jest 47 GB, to wiecej niz wystarczajaco
- NIE zaznaczaj **Specify a custom boot volume size** — niepotrzebne
- NIE zaznaczaj **Use in-transit encryption** — spowalnia i niepotrzebne

### 2.7 Stworz VM

1. Kliknij niebieski przycisk **Create** na dole strony
2. Status zmieni sie na **PROVISIONING** → poczekaj az bedzie **RUNNING** (1-3 minuty)
3. Po uruchomieniu, na stronie detalu VM zobaczysz:
   - **Public IP Address**: np. `129.159.xxx.xxx` — **zanotuj to IP**, bedziesz go potrzebowac
   - **Username**: `opc` (domyslny uzytkownik na Oracle Linux)

### 2.8 Otworz port 80 w Security List

Bez tego krok nginx nie zadziala — przeglądarka nie dostanie sie do serwera.

1. Na stronie detalu VM, w sekcji **Primary VNIC**, kliknij nazwe **Subnet**
   (np. `Public Subnet-ichimoku-vcn`)
2. Na stronie subnetu, w sekcji **Security Lists**, kliknij nazwe security list
   (np. `Default Security List for ichimoku-vcn`)
3. Kliknij **Add Ingress Rules**
4. Wypelnij formularz:
   - **Source Type**: CIDR (domyslne)
   - **Source CIDR**: `0.0.0.0/0`
   - **IP Protocol**: TCP (domyslne)
   - **Source Port Range**: zostaw puste
   - **Destination Port Range**: `80`
   - **Description**: `HTTP`
5. Kliknij **Add Ingress Rules**

Opcjonalnie dodaj tez port 443 (HTTPS) — te same kroki, tylko **Destination Port Range**: `443`.

> **Sprawdzenie**: W security list powinienes teraz widziec reguly:
> - Port 22 (SSH) — byla juz domyslnie
> - Port 80 (HTTP) — wlasnie dodales
> - (opcjonalnie) Port 443 (HTTPS)

## Krok 4: Polacz sie z VM

```bash
# Linux/Mac:
chmod 400 ssh-key.key
ssh -i ssh-key.key opc@<PUBLIC_IP>

# Windows (PowerShell):
ssh -i ssh-key.key opc@<PUBLIC_IP>
```

`<PUBLIC_IP>` znajdziesz w detalu VM w konsoli Oracle.

## Krok 5: Zainstaluj Docker (na VM)

```bash
# Skopiuj i wklej calosc:
sudo dnf install -y dnf-utils
sudo dnf config-manager --add-repo https://download.docker.com/linux/oracle/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker opc

# Otworz port w firewallu VM:
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --reload

# Wyloguj sie i zaloguj ponownie (zeby docker group zadzialal):
exit
```

Potem znowu: `ssh -i ssh-key.key opc@<PUBLIC_IP>`

## Krok 6: Wrzuc kod i uruchom

### Opcja A: Git (zalecane)

Jesli masz repo na GitHubie/Bitbuckecie:

```bash
sudo dnf install -y git
git clone https://github.com/TWOJ_USER/ichimoku.git
cd ichimoku
docker compose up -d --build
```

### Opcja B: SCP (bez repo)

Z lokalnej maszyny (Windows PowerShell):

```powershell
# Spakuj projekt (bez node_modules i build):
tar -czf ichimoku.tar.gz --exclude=node_modules --exclude=.angular --exclude=build --exclude=.gradle --exclude=data --exclude=dist -C c:\Workspace ichimoku

# Wyslij na serwer:
scp -i ssh-key.key ichimoku.tar.gz opc@<PUBLIC_IP>:~/

# Na serwerze:
ssh -i ssh-key.key opc@<PUBLIC_IP>
tar -xzf ichimoku.tar.gz
cd ichimoku
docker compose up -d --build
```

## Krok 7: Sprawdz

```bash
# Logi:
docker compose logs -f

# Status:
docker compose ps

# Otworz w przegladarce:
# http://<PUBLIC_IP>
```

## Przydatne komendy

```bash
# Restart:
docker compose restart

# Przebuduj po zmianach:
docker compose up -d --build

# Stop:
docker compose down

# Logi backendu:
docker compose logs -f backend

# Wejdz do kontenera:
docker compose exec backend sh

# Backup bazy H2:
docker compose cp backend:/app/data ./backup-data
```

## Aktualizacja

```bash
cd ichimoku
git pull
docker compose up -d --build
```

## Opcjonalnie: Darmowa domena + HTTPS

1. Zarejestruj darmowa domene na https://freenom.com lub uzyj subdomain z https://duckdns.org
2. Ustaw DNS A record na `<PUBLIC_IP>`
3. Na serwerze zainstaluj Certbot:

```bash
sudo dnf install -y certbot python3-certbot-nginx
sudo certbot --nginx -d twoja-domena.duckdns.org
```
