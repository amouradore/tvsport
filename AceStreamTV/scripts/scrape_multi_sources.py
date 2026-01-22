import requests
from bs4 import BeautifulSoup
import json
from datetime import datetime
import re

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
                    
                    # Extraire Ã©quipes si format "Team1 vs Team2"
                    teams = event_name.split(" vs ")
                    if len(teams) == 2:
                        home_team = teams[0].strip()
                        away_team = teams[1].strip()
                    else:
                        home_team = event_name
                        away_team = ""
                    
                    matches.append({
                        "time": time_str,
                        "date": datetime.now().strftime("%Y-%m-%d"),
                        "home_team": home_team,
                        "away_team": away_team,
                        "home_logo": "",
                        "away_logo": "",
                        "channels": ["SportsOnline"],
                        "link": url
                    })
        print(f"âœ… SportsOnline: {len(matches)} matchs trouvÃ©s")
    except Exception as e:
        print(f"âŒ Erreur SportsOnline: {e}")
    
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
        
        # Chercher les Ã©vÃ©nements du jour
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
                    
                    matches.append({
                        "time": time_str,
                        "date": datetime.now().strftime("%Y-%m-%d"),
                        "home_team": home_team,
                        "away_team": away_team,
                        "home_logo": "",
                        "away_logo": "",
                        "channels": ["LiveTV"],
                        "link": "https://livetv.sx"
                    })
            except:
                continue
        
        print(f"âœ… LiveTV.sx: {len(matches)} matchs trouvÃ©s")
    except Exception as e:
        print(f"âŒ Erreur LiveTV.sx: {e}")
    
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
                
                # Extract logos
                home_logo = ""
                away_logo = ""
                home_logo_el = match_el.select_one('.match__team--home .team__logo img, .home-team img')
                away_logo_el = match_el.select_one('.match__team--away .team__logo img, .away-team img')
                
                if home_logo_el:
                    home_logo = home_logo_el.get('data-src') or home_logo_el.get('src') or ""
                if away_logo_el:
                    away_logo = away_logo_el.get('data-src') or away_logo_el.get('src') or ""
                
                # Extract competition
                competition = ""
                comp_el = match_el.select_one('.match__competition, .competition')
                if comp_el:
                    competition = comp_el.get_text(strip=True)
                
                channels = []
                for b in match_el.select('.broadcaster__logo, .broadcaster'):
                    alt = b.get('alt') or b.get_text(strip=True)
                    if alt:
                        channels.append(alt)
                
                matches.append({
                    "time": time_str,
                    "date": today_str,
                    "home_team": home_team,
                    "away_team": away_team,
                    "home_logo": home_logo,
                    "away_logo": away_logo,
                    "competition": competition,
                    "channels": channels if channels else ["TV"],
                    "link": "https://www.footmercato.net/matchs/"
                })
            except:
                continue
        
        print(f"âœ… FootMercato: {len(matches)} matchs trouvÃ©s")
    except Exception as e:
        print(f"âŒ Erreur FootMercato: {e}")
    
    return matches

def main():
    print("ðŸ”„ DÃ©but du scraping multi-sources...")
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
    print(f"âœ… Total: {len(all_matches)} matchs sauvegardÃ©s dans matches.json")

if __name__ == "__main__":
    main()
