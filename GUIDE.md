# PrintMonitor — Installation et configuration réseau (local + Livebox 5)

Surveillance depuis Android de **deux Elegoo Centauri Carbon**, d'une **Creality K1** et de leurs **prises Tapo**, avec les caméras intégrées.

## 1. Ce que fait l'application

- Tableau de bord par imprimante : état, températures (buse, plateau, caisson), fichier, progression, couche, temps écoulé/restant, heure de fin estimée.
- Flux caméra en direct (MJPEG) de chaque imprimante.
- Pause / Reprise / Arrêt de l'impression (arrêt avec confirmation).
- Prises Tapo : marche/arrêt, puissance instantanée et consommation du jour (P110/P115), avec garde-fou si la buse est chaude ou une impression en cours.
- Extinction automatique optionnelle : la prise est coupée après la fin d'impression, une fois la buse sous 50 °C (+2 min).
- Notifications : impression terminée, pause (fin de filament ?), erreur, arrêt, perte de connexion pendant une impression.
- 100 % local : aucun cloud Elegoo/Creality/TP-Link. À distance, l'application passe par un VPN.

| Appareil | Protocole | Ports |
|---|---|---|
| Elegoo Centauri Carbon (CC1) | SDCP v3 sur WebSocket | 3030 (contrôle), 3031 (caméra `/video`) |
| Creality K1 / K1C / K1 Max (firmware d'origine) | WebSocket JSON | 9999 (contrôle), 8080 (caméra `/?action=stream`) |
| Prise Tapo P100/P105/P110/P115 | KLAP (HTTP chiffré) | 80 |

## 2. Compiler et installer l'application

1. Installer **Android Studio** (version récente).
2. *File › Open* → choisir le dossier `PrintMonitor`. Laisser la synchronisation Gradle se faire (Android Studio télécharge Gradle 8.9 et les dépendances ; accepter s'il propose de créer le Gradle wrapper).
3. Sur le téléphone : *Paramètres › À propos › Numéro de build* (7 appuis) pour activer les options développeur, puis activer le **débogage USB**.
4. Brancher le téléphone, cliquer **Run ▶**. Ou *Build › Build APK(s)* pour obtenir un APK à installer.
5. Au premier lancement : autoriser les notifications, puis remplir les **Réglages** (adresses IP — voir étape 3).
6. Pour les alertes écran éteint : *Paramètres Android › Applis › PrintMonitor › Batterie* → **Non restreinte**.

## 3. Réseau local (Livebox 5)

### 3.1 Se connecter à la Livebox

1. Sur un appareil connecté à la Livebox, ouvrir `http://192.168.1.1` (ou `http://livebox`).
2. Identifiant `admin`. Mot de passe par défaut : les **8 premiers caractères de la clé Wi-Fi** inscrite sous la Livebox (si vous ne l'avez jamais changé).

### 3.2 Donner une adresse IP fixe à chaque appareil (indispensable)

Sans IP fixe, une imprimante peut changer d'adresse après un redémarrage et l'application ne la retrouve plus.

1. *Paramètres avancés › Réseau › onglet DHCP*.
2. Dans la liste des équipements connectés, repérer chaque appareil (nom ou adresse MAC ; la MAC des imprimantes est visible dans leurs menus réseau, celle des prises dans l'appli Tapo › prise › Informations).
3. Ajouter un **bail statique** pour chacun. Plan conseillé :

| Appareil | IP |
|---|---|
| Raspberry Pi / NAS / PC (VPN, étape 4) | 192.168.1.10 |
| Elegoo Centauri Carbon n° 1 | 192.168.1.50 |
| Creality K1 | 192.168.1.51 |
| Prise Tapo de la Centauri n° 1 | 192.168.1.52 |
| Prise Tapo de la K1 | 192.168.1.53 |
| Elegoo Centauri Carbon n° 2 | 192.168.1.54 |
| Prise Tapo de la Centauri n° 2 | 192.168.1.55 |

4. Redémarrer chaque appareil (ou couper/rallumer sa prise) pour qu'il prenne sa nouvelle adresse. Vérifier l'IP sur l'écran de chaque imprimante.

### 3.3 Préparer les appareils

- **Centauri Carbon** : rien à activer, le mode réseau local est actif par défaut. La caméra n'accepte **qu'un seul flux à la fois** : fermer la vue caméra d'Elegoo Slicer / de l'appli Elegoo si l'image ne s'affiche pas dans PrintMonitor.
- **Creality K1** : rien à activer pour l'état (port 9999). Vérifier la caméra dans un navigateur : `http://192.168.1.51:8080/?action=stream`. Si rien ne s'affiche, voir le dépannage (§ 6).
- **Prises Tapo** : dans l'appli Tapo, *Moi › Services tiers › Compatibilité tierce* → **activer**. Sur les firmwares récents (1.4.x), sans cette option la prise refuse les connexions locales. Les identifiants à saisir dans PrintMonitor sont ceux du compte Tapo (e-mail + mot de passe).

### 3.4 Remplir les réglages de l'application

Saisir les IP de l'étape 3.2 (un nom personnalisé peut être donné à chaque imprimante pour distinguer les deux Centauri), l'e-mail et le mot de passe Tapo, puis **Enregistrer**. Le tableau de bord doit afficher les deux imprimantes en ligne en quelques secondes (sur le Wi-Fi de la maison).

## 4. Accès depuis l'extérieur

> ⚠️ **Ne jamais ouvrir dans la Livebox (NAT/PAT ou DMZ) les ports des imprimantes ou des prises** (80, 3030, 3031, 8080, 9999). Ces services n'ont **ni mot de passe ni chiffrement** : n'importe qui sur Internet pourrait lancer, arrêter ou modifier une impression, ou allumer une imprimante — risque d'incendie réel.

La bonne méthode : un **VPN**. Le téléphone rejoint virtuellement le réseau de la maison et l'application fonctionne avec **les mêmes adresses 192.168.1.x**, sans aucune modification. Il faut un petit appareil allumé en permanence à la maison : Raspberry Pi (idéal), NAS, ou PC.

### Option A — Tailscale (recommandé : rien à ouvrir sur la Livebox)

Gratuit pour un usage personnel, chiffré (WireGuard), fonctionne même si l'IP publique change.

**Sur le Raspberry Pi (192.168.1.10)** :

```bash
curl -fsSL https://tailscale.com/install.sh | sh
echo 'net.ipv4.ip_forward = 1' | sudo tee /etc/sysctl.d/99-tailscale.conf
sudo sysctl -p /etc/sysctl.d/99-tailscale.conf
sudo tailscale up --advertise-routes=192.168.1.0/24
```

Ouvrir le lien affiché et se connecter (compte Google, Microsoft, Apple… au choix).

**Dans la console Tailscale** (login.tailscale.com › Machines) :

1. Sur la machine du Pi : *… › Edit route settings* → cocher **192.168.1.0/24** (approbation du sous-réseau).
2. *… › Disable key expiry* pour que le Pi ne se déconnecte pas au bout de 180 jours.

**Sur le téléphone** : installer **Tailscale** (Play Store), se connecter avec le même compte, activer le VPN.

**Test** : couper le Wi-Fi, rester en 4G/5G, ouvrir PrintMonitor → les imprimantes et caméras doivent s'afficher.

*Variante NAS/PC* : Tailscale existe pour Synology, Windows, macOS ; activer la fonction « subnet router » avec la route 192.168.1.0/24.

### Option B — WireGuard auto-hébergé (avec règle NAT sur la Livebox)

Totalement indépendant d'un service tiers, mais demande une ouverture de port (uniquement celle du VPN).

**1. Installer WireGuard sur le Pi avec PiVPN** :

```bash
curl -L https://install.pivpn.io | bash
```

Choisir **WireGuard**, port **51820**, et pour « Public IP or DNS », le nom DynDNS de l'étape 3 ci-dessous. Puis créer le profil du téléphone :

```bash
pivpn add -n telephone
pivpn -qr telephone      # affiche un QR code
```

**2. Règle NAT/PAT sur la Livebox 5** :

1. `http://192.168.1.1` › *Paramètres avancés › Réseau › onglet NAT/PAT*.
2. Ajouter une règle personnalisée :
   - Application/Service : `WireGuard`
   - Port interne : `51820` — Port externe : `51820`
   - Protocole : **UDP**
   - Équipement : le Raspberry Pi (192.168.1.10)
3. Cocher **Activer** et enregistrer.

**3. Nom DynDNS (l'IP publique Orange peut changer)** :

1. Créer un nom gratuit chez un fournisseur proposé par la Livebox (ex. No-IP).
2. *Paramètres avancés › Réseau › onglet DynDNS* → choisir le service, saisir nom d'hôte, identifiant, mot de passe → Ajouter.

**4. Pare-feu** : laisser le niveau par défaut (*Moyen*) ; la règle NAT suffit.

**5. Sur le téléphone** : installer **WireGuard** (Play Store), *+ › Scanner un QR code*, activer le tunnel. Tester en 4G comme pour l'option A.

### Conflit d'adresses à connaître

Si vous êtes connecté en Wi-Fi chez quelqu'un dont le réseau est aussi en 192.168.1.x (très courant), le VPN ne peut pas savoir quel 192.168.1.50 joindre. Solutions : rester en 4G/5G, ou changer une fois pour toutes le réseau de votre Livebox (*Paramètres avancés › Réseau › DHCP* : adresse de la Livebox, ex. 192.168.20.1), puis refaire les baux statiques et les IP dans l'application (et la route Tailscale en 192.168.20.0/24).

## 5. Sécurité

- Aucun port d'imprimante ou de prise exposé sur Internet : seul le VPN y donne accès.
- Le mot de passe Tapo est stocké dans les préférences privées de l'application, sur le téléphone uniquement.
- L'arrêt d'une impression et la coupure d'une prise chaude demandent une confirmation.
- Une imprimante 3D reste un appareil chauffant : la surveillance à distance ne remplace pas un détecteur de fumée près des imprimantes.

## 6. Dépannage

| Symptôme | Cause probable / solution |
|---|---|
| Centauri « Hors ligne » | Vérifier l'IP. La Centauri accepte 5 connexions WebSocket maximum : fermer d'autres outils connectés (Home Assistant, scripts…). |
| Caméra Centauri noire | Un seul flux possible : fermer la caméra dans Elegoo Slicer / l'appli Elegoo, puis masquer/afficher la caméra dans PrintMonitor. |
| Caméra K1 « HTTP » ou injoignable | Certains firmwares K1 récents (ex. 1.3.5.22) ne publient plus le flux MJPEG du port 8080. Solutions : revenir au firmware précédent, ou (imprimante rootée + Helper Script) appliquer le correctif mjpg-streamer documenté sur le forum Creality, puis indiquer l'URL dans les réglages (ex. `http://IP:4408/webcam/?action=stream`). |
| K1C « 2025 » | Cette révision diffuse sa caméra en WebRTC (port 8000), pas en MJPEG : non pris en charge par la vue caméra intégrée. |
| Prise « Handshake refusé (HTTP 403) » | Activer *Compatibilité tierce* dans l'appli Tapo ; vérifier l'IP de la prise. |
| Prise « Identifiants Tapo incorrects » | E-mail / mot de passe du compte Tapo (respecter majuscules/minuscules). |
| Fonctionne en Wi-Fi, pas en 4G | VPN désactivé sur le téléphone, route 192.168.1.0/24 non approuvée dans Tailscale, ou règle NAT UDP 51820 absente (WireGuard). |
| Pas d'alerte écran éteint | Batterie de l'appli en « Non restreinte », surveillance en arrière-plan activée dans les réglages, notifications autorisées. |

## 7. Pistes d'évolution

- Découverte automatique de la Centauri (diffusion UDP `M99999` sur le port 3000).
- Widget d'écran d'accueil avec la progression.
- Prise en charge de la Centauri Carbon 2 (MQTT, port 1883) ou d'une K1 rootée via Moonraker (port 7125).
