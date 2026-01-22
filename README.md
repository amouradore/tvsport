# 📺 TVSport - Scraper de Matchs en Direct

Ce dépôt contient un système automatisé de scraping pour récupérer les matchs du jour depuis plusieurs sources web.

## 🎯 Objectif

Fournir un fichier `matches.json` mis à jour automatiquement toutes les 10 minutes avec la liste des matchs sportifs disponibles en streaming.

## 📁 Structure

```
tvsport/
├── matches.json                          # ✅ Fichier JSON avec les matchs du jour
├── scripts/
│   ├── scrape_multi_sources.py          # 🔄 Script de scraping multi-sources
│   └── requirements.txt                  # 📦 Dépendances Python
└── .github/
    └── workflows/
        └── scrape_matches_multi.yml      # ⏰ Action GitHub (toutes les 10 min)
```

## 🚀 Fonctionnement

### Sources de données

Le script scrape **3 sources** différentes :

1. **SportsOnline.ci** - Événements sportifs avec liens directs
2. **LiveTV.sx** - Matchs en direct
3. **FootMercato.net** - Matchs de football

### Format du fichier matches.json

```json
[
  {
    "time": "20:00",
    "date": "2026-01-22",
    "home_team": "Real Madrid",
    "away_team": "Barcelona",
    "home_logo": "",
    "away_logo": "",
    "competition": "La Liga",
    "channels": ["LaLiga TV", "beIN Sports"],
    "link": "acestream://abc123def456"
  }
]
```

## ⚙️ Installation locale

### Prérequis
- Python 3.9+
- pip

### Installation

```bash
# Cloner le dépôt
git clone https://github.com/amouradore/tvsport.git
cd tvsport

# Installer les dépendances
pip install -r scripts/requirements.txt

# Exécuter le scraper
python scripts/scrape_multi_sources.py
```

## 🤖 Automatisation GitHub Actions

Le fichier `matches.json` est automatiquement mis à jour :
- ⏰ **Toutes les 10 minutes** via GitHub Actions
- 🔄 **Commit automatique** des changements
- 📊 **Log** du nombre de matchs trouvés

### Activer l'automatisation

1. Pusher ce dépôt vers GitHub
2. L'action GitHub se déclenchera automatiquement
3. Vérifier les exécutions dans l'onglet "Actions"

### Exécution manuelle

Vous pouvez déclencher manuellement l'action depuis GitHub :
1. Aller dans l'onglet "Actions"
2. Sélectionner "Scrape Matches - Multi Sources"
3. Cliquer sur "Run workflow"

## 📱 Intégration dans votre App Android

Dans votre application Android (AceStreamTV), consommez le fichier JSON :

```kotlin
// URL du fichier JSON
val matchesUrl = "https://raw.githubusercontent.com/amouradore/tvsport/main/matches.json"

// Récupérer et parser les matchs
val matches = fetchMatches(matchesUrl)
```

## 🔧 Configuration

### Modifier la fréquence de mise à jour

Éditez `.github/workflows/scrape_matches_multi.yml` :

```yaml
on:
  schedule:
    - cron: '*/10 * * * *'  # Modifier ici (actuellement 10 min)
```

Exemples :
- `*/5 * * * *` = Toutes les 5 minutes
- `*/15 * * * *` = Toutes les 15 minutes
- `0 * * * *` = Toutes les heures

### Ajouter une nouvelle source

Éditez `scripts/scrape_multi_sources.py` et ajoutez votre fonction :

```python
def scrape_nouvelle_source():
    matches = []
    # Votre code de scraping ici
    return matches

# Dans main()
all_matches.extend(scrape_nouvelle_source())
```

## 📊 Monitoring

Vérifiez que le système fonctionne :

```bash
# Vérifier la dernière mise à jour
git log -1 matches.json

# Voir le contenu
cat matches.json
```

## 🐛 Dépannage

### Le fichier matches.json est vide

- Vérifier que les sites sources sont accessibles
- Consulter les logs GitHub Actions
- Tester le script localement

### L'action GitHub ne se déclenche pas

- Vérifier les permissions du workflow
- S'assurer que le dépôt n'est pas privé (ou activer Actions pour dépôt privé)

## 📝 Licence

MIT License - Libre d'utilisation

## 👤 Auteur

Créé pour l'application AceStreamTV
