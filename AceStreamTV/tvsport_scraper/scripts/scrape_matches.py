import requests
from bs4 import BeautifulSoup
import json
import datetime
import re
import os
import time

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
    
    # Select matches
    match_elements = soup.select('.match')
    if not match_elements:
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
            
            def process_logo_url(img_el):
                if not img_el: return ""
                url = img_el.get('data-src') or img_el.get('src') or ""
                # Remove query params for cleaner URL if needed, but important part is scheme
                if url.startswith("//"):
                    url = "https:" + url
                return url

            home_logo = process_logo_url(home_logo_el)
            away_logo = process_logo_url(away_logo_el)
            
            # Match Link
            link_el = match_el.select_one('a.match__link')
            match_link = ""
            if link_el:
                href = link_el.get('href')
                if href:
                    if href.startswith("http"):
                        match_link = href
                    else:
                        match_link = "https://www.footmercato.net" + href
            
            # TV Channels Extraction
            channels = []
            
            # 1. Try from list view (broadcaster logos)
            broadcasters_el = match_el.select('.broadcaster__logo')
            for b in broadcasters_el:
                alt = b.get('alt')
                if alt: channels.append(alt)
            
            # 2. Visit detail page for comprehensive channels
            # Essential for user requirement
            if match_link:
                try:
                    # Generic sleep to avoid aggressive rate limiting
                    # time.sleep(0.1) 
                    
                    # print(f"  > Details: {match_link}")
                    detail_resp = requests.get(match_link, headers=headers, timeout=5)
                    
                    if detail_resp.status_code == 200:
                        detail_soup = BeautifulSoup(detail_resp.content, 'html.parser')
                        
                        # Strategy A: Logo images in details
                        for img in detail_soup.select('.broadcaster__logo'):
                            alt = img.get('alt')
                            if alt: channels.append(alt)
                            
                        # Strategy B: Text mentions in potential TV blocks
                        # Often inside .matchInfo or .broadcasters text
                        # We scan for known keywords if strict classes fail
                        full_text = detail_soup.get_text(" ", strip=True).lower()
                        
                        known_channels = [
                            "bein sports", "canal+", "rmc sport", "lequipe", "tf1", "m6", "eurosport",
                            "dazn", "prime video", "amazon"
                        ]
                        
                        # Simple keyword check if we found nothing specific
                        if not channels:
                            for kc in known_channels:
                                if kc in full_text:
                                    # Very basic heuristic: if exact match in text, assume diff
                                    # But verify it's near "diffusion" words to avoid false positives
                                    if "diffusion" in full_text or "tv" in full_text:
                                        # channels.append(kc) # Commented out to avoid noise, stick to strict first
                                        pass

                except Exception as eDetail:
                    # Ignore detail fetch errors to keep the match
                    # print(f"Detail fetch failed: {eDetail}")
                    pass

            # Deduplicate
            channels = list(set([c for c in channels if c]))
            
            # Normalize channel names for App matching
            final_channels = []
            for c in channels:
                final_channels.append(c)
                c_lower = c.lower()
                # Aliases
                if "bein" in c_lower: final_channels.append("beIN Sports")
                if "canal" in c_lower: final_channels.append("Canal+")
                if "rmc" in c_lower: final_channels.append("RMC Sport")
                if "dazn" in c_lower: final_channels.append("DAZN")

            final_channels = list(set(final_channels))

            match_data = {
                "time": time_str,
                "date": today_str,
                "home_team": home_team,
                "away_team": away_team,
                "home_logo": home_logo,
                "away_logo": away_logo,
                "channels": final_channels,
                "link": match_link
            }
            
            matches.append(match_data)
            
        except Exception as e:
            print(f"Error parsing match element: {e}")
            continue

    print(f"Found {len(matches)} matches.")
    return matches

def save_matches(matches):
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(matches, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(matches)} matches to {OUTPUT_FILE}")

if __name__ == "__main__":
    matches = scrape_matches()
    save_matches(matches)
