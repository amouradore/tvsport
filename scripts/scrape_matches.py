import requests
from bs4 import BeautifulSoup
import json
import datetime
import re
import os

# Configuration
URL = "https://www.footmercato.net/matchs/"
OUTPUT_FILE = "matches.json"

def scrape_matches():
    print(f"Scraping {URL}...")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
    
    try:
        response = requests.get(URL, headers=headers)
        response.raise_for_status()
    except Exception as e:
        print(f"Error fetching page: {e}")
        return []

    soup = BeautifulSoup(response.content, 'html.parser')
    
    matches = []
    
    # FootMercato structure:
    # Matches are often grouped by competition in divs
    # We look for match items. Class names might vary, so we look for structural elements.
    # Usually: .match-card or similar.
    
    # Strategy: Find all elements that look like a match row
    # Adjust selectors based on inspection or generic classes
    
    # Common FootMercato selectors (may need adjustment if site changes)
    # They use 'match' class often in lists.
    
    # Trying to find the main container for "Today"
    # On /matchs/ page, it lists matches for the current day by default.
    
    match_elements = soup.select('.match') 
    
    if not match_elements:
        # Fallback or different layout check
        match_elements = soup.select('.matchList__item')
        
    print(f"Found {len(match_elements)} potential match elements.")

    today_str = datetime.datetime.now().strftime("%Y-%m-%d")

    for match_el in match_elements:
        try:
            # Time
            time_el = match_el.select_one('.match__time')
            if not time_el:
                continue
            time_str = time_el.get_text(strip=True)
            
            # Teams
            home_team_el = match_el.select_one('.match__team--home .team__name')
            away_team_el = match_el.select_one('.match__team--away .team__name')
            
            if not home_team_el or not away_team_el:
                continue
                
            home_team = home_team_el.get_text(strip=True)
            away_team = away_team_el.get_text(strip=True)
            
            # Logos
            home_logo_el = match_el.select_one('.match__team--home .team__logo img')
            away_logo_el = match_el.select_one('.match__team--away .team__logo img')
            
            home_logo = home_logo_el.get('data-src') or home_logo_el.get('src') if home_logo_el else ""
            away_logo = away_logo_el.get('data-src') or away_logo_el.get('src') if away_logo_el else ""
            
            # TV Channels
            # Sometimes hidden or in a detail link. On the main list, sometimes icons appear.
            # If not present, we might need to parse the match detail page, but let's stick to list first.
            # FootMercato sometime puts TV info in .match__info or .broadcasters
            
            channels = []
            broadcasters_el = match_el.select('.broadcaster__logo')
            for b in broadcasters_el:
                alt = b.get('alt')
                if alt:
                    channels.append(alt)
            
            # If no broadcasters found in list, check if there is a link to match details
            link_el = match_el.select_one('a.match__link')
            match_link = ""
            if link_el:
                match_link = "https://www.footmercato.net" + link_el.get('href')
            
            # Basic data structure
            match_data = {
                "time": time_str, # Format HH:mm
                "date": today_str,
                "home_team": home_team,
                "away_team": away_team,
                "home_logo": home_logo,
                "away_logo": away_logo,
                "channels": channels,
                "link": match_link
            }
            
            matches.append(match_data)
            
        except Exception as e:
            print(f"Error parsing match element: {e}")
            continue

    return matches

def save_matches(matches):
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(matches, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(matches)} matches to {OUTPUT_FILE}")

if __name__ == "__main__":
    matches = scrape_matches()
    save_matches(matches)
