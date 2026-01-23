import requests
from bs4 import BeautifulSoup
import json
from datetime import datetime
import re

# API pour récupérer les logos d'équipes (gratuit et sans clé API)
def get_team_logo(team_name):
    """Récupère le logo d'une équipe via l'API team-lookup"""
    try:
        # Essayer d'abord avec TheSportsDB (gratuit)
        team_clean = team_name.strip()
        # URL encode le nom
        encoded_name = requests.utils.quote(team_clean)
        url = f"https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t={encoded_name}"
        response = requests.get(url, timeout=5)
        if response.status_code == 200:
            data = response.json()
            if data.get('teams') and len(data['teams']) > 0:
                team_data = data['teams'][0]
                # Retourner le badge/logo de l'équipe
                logo = team_data.get('strTeamBadge') or team_data.get('strTeamLogo')
                if logo:
                    return logo
    except:
        pass
    return ""

# Mapping des chaînes de diffusion vers les noms dans le M3U
CHANNEL_MAPPING = {
    # Espagne
    "LaLiga TV": ["M. LaLiga", "LaLiga", "M+ LaLiga"],
    "beIN Sports": ["Bein Sports", "beIN SPORTS"],
    "DAZN LaLiga": ["Dazn Laliga", "DAZN LaLiga"],
    "M+ LaLiga": ["M. LaLiga"],
    "Movistar LaLiga": ["M. LaLiga"],
    
    # France
    "Canal+": ["Canal+", "Canal Plus"],
    "Prime Video": ["Amazon Prime"],
    "beIN Sports 1": ["Bein Sports 1"],
    "beIN Sports 2": ["Bein Sports 2"],
    
    # Angleterre
    "Sky Sports": ["Sky Sport"],
    "BT Sport": ["BT Sport"],
    "Sky Sports Premier League": ["Sky Sport Premier League"],
    
    # Allemagne
    "Sky Deutschland": ["Sky Sport Bundesliga", "DAZN 1 DE"],
    
    # Italie
    "DAZN": ["DAZN 1", "DAZN 2", "DAZN"],
    "Sky Sport": ["Sky Sport Calcio"],
    
    # Portugal
    "Sport TV": ["Sport TV", "Eleven Sport"],
    "Eleven Sports": ["Eleven Sport"],
    
    # Général
    "Eurosport": ["Eurosport 1", "Eurosport 2"],
    "ESPN": ["ESPN", "ESPN 1", "ESPN 2"],
    
    # Par défaut - chaînes génériques espagnoles qui ont beaucoup de contenu
    "TV": ["M. LaLiga", "DAZN 1", "Movistar Deportes"],
}

def map_broadcaster_to_channel(broadcaster):
    """Convertit un nom de broadcaster en noms de chaînes du M3U"""
    broadcaster_clean = broadcaster.strip()
    
    # Vérifier dans le mapping
    if broadcaster_clean in CHANNEL_MAPPING:
        return CHANNEL_MAPPING[broadcaster_clean]
    
    # Essayer de trouver une correspondance partielle
    for key, values in CHANNEL_MAPPING.items():
        if key.lower() in broadcaster_clean.lower() or broadcaster_clean.lower() in key.lower():
            return values
    
    # Par défaut, retourner le nom original + quelques chaînes populaires
    return [broadcaster_clean, "M. LaLiga", "DAZN 1"]

def scrape_sportsonline():
    """Scrape sportsonline.ci pour les matchs du jour"""
    matches = []
    try:
        response = requests.get("https://sportsonline.ci/prog.txt", timeout=10)
        response.raise_for_status()
        content = response.text
        
        day_current = datetime.now().strftime("%A").upper()
        day_found = False
        
        for line in content.split("\n"):
            line = line.strip()
            if line.upper() in ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]:
                day_found = (line.upper() == day_current)
                continue
            
            if day_found and "|" in line:
                match_data = re.match(r"^(\d{2}:\d{2})\s+(.*?)\s+\|\s+(https?://\S+)", line)
                if match_data:
                    time_str = match_data.group(1)
                    event_name = match_data.group(2).strip()
                    url = match_data.group(3)
                    
                    # Extraire équipes si format "Team1 vs Team2"
                    teams = event_name.split(" vs ")
                    if len(teams) == 2:
                        home_team = teams[0].strip()
                        away_team = teams[1].strip()
                    else:
                        home_team = event_name
                        away_team = ""
                    
                    # Récupérer les logos des équipes
                    home_logo = get_team_logo(home_team) if home_team else ""
                    away_logo = get_team_logo(away_team) if away_team else ""
                    
                    # Mapper les chaînes génériques vers les vraies chaînes du M3U
                    # Pour SportsOnline, on ajoute les chaînes principales espagnoles
                    channel_list = ["M. LaLiga", "DAZN 1", "DAZN LaLiga", "Movistar Deportes"]
                    
                    matches.append({
                        "time": time_str,
                        "date": datetime.now().strftime("%Y-%m-%d"),
                        "home_team": home_team,
                        "away_team": away_team,
                        "home_logo": home_logo,
                        "away_logo": away_logo,
                        "competition": "",
                        "channels": channel_list,
                        "link": url
                    })
        print(f"✅ SportsOnline: {len(matches)} matchs trouvés")
    except Exception as e:
        print(f"❌ Erreur SportsOnline: {e}")
    
    return matches

def scrape_livetv_sx():
    """Scrape LiveTV.sx pour les matchs du jour"""
    matches = []
    try:
        url = "https://livetv.sx/enx/allupcomingsports/1/"
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }
        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # Chercher les événements du jour
        for event in soup.select('.event'):
            try:
                time_el = event.select_one('.time')
                teams_el = event.select_one('.teams')
                
                if time_el and teams_el:
                    time_str = time_el.get_text(strip=True)
                    teams_text = teams_el.get_text(strip=True)
                    
                    teams = teams_text.split(" - ")
                    home_team = teams[0].strip() if len(teams) > 0 else teams_text
                    away_team = teams[1].strip() if len(teams) > 1 else ""
                    
                    # Récupérer les logos
                    home_logo = get_team_logo(home_team) if home_team else ""
                    away_logo = get_team_logo(away_team) if away_team else ""
                    
                    # Chaînes génériques populaires
                    channel_list = ["M. LaLiga", "DAZN 1", "Sky Sport", "Bein Sports 1"]
                    
                    matches.append({
                        "time": time_str,
                        "date": datetime.now().strftime("%Y-%m-%d"),
                        "home_team": home_team,
                        "away_team": away_team,
                        "home_logo": home_logo,
                        "away_logo": away_logo,
                        "competition": "",
                        "channels": channel_list,
                        "link": "https://livetv.sx"
                    })
            except:
                continue
        
        print(f"✅ LiveTV.sx: {len(matches)} matchs trouvés")
    except Exception as e:
        print(f"❌ Erreur LiveTV.sx: {e}")
    
    return matches

def scrape_footmercato():
    """Scrape FootMercato pour les matchs du jour"""
    matches = []
    try:
        url = "https://www.footmercato.net/matchs/"
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }
        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()
        soup = BeautifulSoup(response.content, 'html.parser')
        
        match_elements = soup.select('.match, .matchList__item')
        today_str = datetime.now().strftime("%Y-%m-%d")
        
        for match_el in match_elements:
            try:
                time_el = match_el.select_one('.match__time, .time')
                if not time_el:
                    continue
                time_str = time_el.get_text(strip=True)
                
                home_team_el = match_el.select_one('.match__team--home .team__name, .home-team')
                away_team_el = match_el.select_one('.match__team--away .team__name, .away-team')
                
                if not home_team_el or not away_team_el:
                    continue
                
                home_team = home_team_el.get_text(strip=True)
                away_team = away_team_el.get_text(strip=True)
                
                # Récupérer les logos
                home_logo = get_team_logo(home_team) if home_team else ""
                away_logo = get_team_logo(away_team) if away_team else ""
                
                channels = []
                for b in match_el.select('.broadcaster__logo, .broadcaster'):
                    alt = b.get('alt') or b.get_text(strip=True)
                    if alt:
                        channels.append(alt)
                
                # Mapper les chaînes vers les noms du M3U
                mapped_channels = []
                for ch in channels:
                    mapped_channels.extend(map_broadcaster_to_channel(ch))
                
                # Dédupliquer
                mapped_channels = list(dict.fromkeys(mapped_channels))
                
                matches.append({
                    "time": time_str,
                    "date": today_str,
                    "home_team": home_team,
                    "away_team": away_team,
                    "home_logo": home_logo,
                    "away_logo": away_logo,
                    "competition": "",
                    "channels": mapped_channels if mapped_channels else ["M. LaLiga", "DAZN 1"],
                    "link": "https://www.footmercato.net/matchs/"
                })
            except:
                continue
        
        print(f"✅ FootMercato: {len(matches)} matchs trouvés")
    except Exception as e:
        print(f"❌ Erreur FootMercato: {e}")
    
    return matches

def main():
    print("🔄 Début du scraping multi-sources...")
    print("=" * 50)
    
    all_matches = []
    
    # Scraper toutes les sources
    all_matches.extend(scrape_sportsonline())
    all_matches.extend(scrape_livetv_sx())
    all_matches.extend(scrape_footmercato())
    
    # Trier par heure
    all_matches.sort(key=lambda x: x.get("time", "00:00"))
    
    # Sauvegarder
    with open("matches.json", "w", encoding="utf-8") as f:
        json.dump(all_matches, f, ensure_ascii=False, indent=2)
    
    print("=" * 50)
    print(f"✅ Total: {len(all_matches)} matchs sauvegardés dans matches.json")

if __name__ == "__main__":
    main()
