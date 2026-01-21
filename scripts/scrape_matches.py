import requests
from bs4 import BeautifulSoup
import json
import datetime
import re
import os

# Configuration
# TV-Sports.fr is usually reliable and simple
URL = "https://tv-sports.fr/"
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
    
    # Debug: Print title
    print("Page Title:", soup.title.string if soup.title else "No title")

    # TV-Sports.fr typically lists matches in blocks.
    # We look for links that look like a match title "Team A / Team B"
    
    # Find all anchor tags
    links = soup.find_all('a')
    
    today_str = datetime.datetime.now().strftime("%Y-%m-%d")
    
    print(f"Found {len(links)} links. Analyzing...")
    
    seen_links = set()

    for link in links:
        text = link.get_text(strip=True)
        href = link.get('href', '')
        
        # Checking for "Team / Team" pattern
        if " / " in text and href not in seen_links:
            # This is likely a match link
            # Search for container to find time and channels
            # Usually the <a> is inside a <li> or <div> representing the match row
            
            container = link.find_parent('div') # Try div first
            if not container:
                container = link.find_parent('li')
            
            if not container:
                continue

            # Time extraction
            # Look for HH:mm or HHhmm pattern in the container text
            container_text = container.get_text(" ", strip=True)
            time_match = re.search(r'(\d{1,2})[h:](\d{2})', container_text)
            
            time_str = "21:00" # Default
            if time_match:
                time_str = f"{time_match.group(1)}:{time_match.group(2)}"
            
            # Teams
            teams = text.split(" / ")
            if len(teams) < 2:
                continue
            home_team = teams[0].strip()
            away_team = teams[1].strip()
            
            # Logos (Optional, often hard to get generically)
            home_logo = "" 
            away_logo = ""
            
            # Channels
            # Look for images in the container that are NOT the team logos (hard to distinguish without classes)
            # Typically channel logos have 'alt' text with channel name
            channels = []
            images = container.find_all('img')
            for img in images:
                alt = img.get('alt', '')
                src = img.get('src', '')
                # Filter out likely clutter
                if "club" not in src and "team" not in src and len(alt) > 2:
                    channels.append(alt)
                elif "bein" in src.lower():
                    channels.append("beIN Sports")
                elif "canal" in src.lower():
                    channels.append("Canal+")

            # Fallback: if text mentions channels
            lower_text = container_text.lower()
            if "bein" in lower_text: channels.append("beIN Sports")
            if "canal" in lower_text: channels.append("Canal+")
            if "rmc" in lower_text: channels.append("RMC Sport")
            if "lequipe" in lower_text: channels.append("L'Equipe")

            # Remove duplicates
            channels = list(set(channels))
            
            match_data = {
                "time": time_str,
                "date": today_str,
                "home_team": home_team,
                "away_team": away_team,
                "home_logo": home_logo,
                "away_logo": away_logo,
                "channels": channels,
                "link": href if href.startswith("http") else URL + href.strip("/")
            }
            
            matches.append(match_data)
            seen_links.add(href)

    print(f"Found {len(matches)} matches.")
    return matches

def save_matches(matches):
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(matches, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(matches)} matches to {OUTPUT_FILE}")

if __name__ == "__main__":
    matches = scrape_matches()
    save_matches(matches)
