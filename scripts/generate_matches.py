#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate matches.json and matches_other.json from eventos.m3u
With team logos from the logos/ folder
"""
import json
import re
import os
import unicodedata
import urllib.parse
from datetime import datetime

EXCLUDED = ["liga fem", "1rfef", "segunda", "acb", "ehf europeo", 
            "liga nacional juvenil", "liga guerreras", "2rfef", 
            "las carreras", "open australia wta", "wta"]

LOGOS_URL = "https://raw.githubusercontent.com/amouradore/tvsport/main/logos"

# Team name aliases: key = name in eventos.m3u (lowercase), value = normalized logo filename
ALIASES = {
    # Spain
    "at. madrid": "atlético de madrid", "atl. madrid": "atlético de madrid", 
    "atletico": "atlético de madrid", "atletico madrid": "atlético de madrid",
    "atletico de madrid": "atlético de madrid",
    "barcelona": "fc barcelona", "barca": "fc barcelona", "fc barcelona": "fc barcelona",
    "real madrid": "real madrid", "r. madrid": "real madrid",
    "sevilla": "sevilla fc", "sevilla fc": "sevilla fc",
    "valencia": "valencia cf", "valencia cf": "valencia cf",
    "villarreal": "villarreal cf", "villarreal cf": "villarreal cf",
    "athletic": "athletic bilbao", "athletic bilbao": "athletic bilbao",
    "betis": "real betis balompié", "real betis": "real betis balompié",
    "celta": "celta de vigo", "celta vigo": "celta de vigo",
    "osasuna": "ca osasuna", "mallorca": "rcd mallorca", "rcd mallorca": "rcd mallorca",
    "girona": "girona fc", "girona fc": "girona fc",
    "alaves": "deportivo alavés", "d. alaves": "deportivo alavés",
    "espanyol": "rcd espanyol barcelona",
    "real sociedad": "real sociedad", "r. sociedad": "real sociedad",
    "real oviedo": "real oviedo",
    "rayo": "rayo vallecano", "rayo vallecano": "rayo vallecano",
    "levante": "levante ud", "getafe": "getafe cf",
    # England
    "arsenal": "arsenal fc", "arsenal fc": "arsenal fc",
    "liverpool": "liverpool fc", "liverpool fc": "liverpool fc",
    "chelsea": "chelsea fc", "chelsea fc": "chelsea fc",
    "manchester utd.": "manchester united", "manchester utd": "manchester united",
    "man utd": "manchester united", "man. utd": "manchester united",
    "man city": "manchester city", "man. city": "manchester city",
    "manchester city": "manchester city",
    "tottenham": "tottenham hotspur", "tottenham hotspur": "tottenham hotspur",
    "newcastle": "newcastle united", "newcastle united": "newcastle united",
    "aston villa": "aston villa",
    "west ham": "west ham united", "west ham united": "west ham united",
    "crystal palace": "crystal palace",
    "brighton": "brighton & hove albion",
    "everton": "everton fc", "everton fc": "everton fc",
    "fulham": "fulham fc", "fulham fc": "fulham fc",
    "brentford": "brentford fc", "brentford fc": "brentford fc",
    "wolves": "wolverhampton wanderers", "wolverhampton": "wolverhampton wanderers",
    "bournemouth": "afc bournemouth", "afc bournemouth": "afc bournemouth",
    "nottingham": "nottingham forest", "nottingham forest": "nottingham forest",
    "nott'm forest": "nottingham forest",
    "ipswich": "ipswich town", "ipswich town": "ipswich town",
    "leicester": "leicester city", "leicester city": "leicester city",
    "southampton": "southampton fc",
    # Italy
    "milan": "ac milan", "ac milan": "ac milan",
    "inter": "inter milan", "inter milan": "inter milan",
    "juventus": "juventus fc", "juve": "juventus fc",
    "roma": "as roma", "as roma": "as roma",
    "lazio": "ss lazio", "ss lazio": "ss lazio",
    "napoli": "ssc napoli", "ssc napoli": "ssc napoli",
    "atalanta": "atalanta bc", "fiorentina": "acf fiorentina",
    "bologna": "bologna fc 1909", "torino": "torino fc",
    # Germany
    "bayern": "bayern munich", "bayern munich": "bayern munich",
    "dortmund": "borussia dortmund", "b. dortmund": "borussia dortmund",
    "leipzig": "rb leipzig", "rb leipzig": "rb leipzig",
    "leverkusen": "bayer 04 leverkusen", "b. leverkusen": "bayer 04 leverkusen",
    "frankfurt": "eintracht frankfurt", "e. frankfurt": "eintracht frankfurt",
    # France
    "psg": "psg-paris saint-germain", "paris": "psg-paris saint-germain",
    "paris saint-germain": "psg-paris saint-germain",
    "marseille": "olympique marseille", "om": "olympique marseille",
    "lyon": "olympique lyon", "ol": "olympique lyon",
    "monaco": "as monaco", "lille": "losc lille",
    # Portugal
    "porto": "fc porto", "fc porto": "fc porto",
    "benfica": "sl benfica", "sl benfica": "sl benfica",
    "sporting": "sporting cp", "sporting cp": "sporting cp",
    # Netherlands
    "ajax": "ajax amsterdam", "psv": "psv eindhoven",
    "feyenoord": "feyenoord rotterdam",
    # Saudi Arabia
    "al nassr": "nassr", "al-nassr": "nassr",
    "al hilal": "hilal", "al-hilal": "hilal",
    "al ittihad": "ittihad", "al-ittihad": "ittihad",
    "al taawoun": "taawoun", "al taawoun fc": "taawoun",
    "al shabab": "shabab", "al fateh": "fateh",
    "al ettifaq": "ettifaq", "al fayha": "fayha",
    "al wehda": "wehda", "al tai": "tai",
    "al raed": "raed", "al damac": "damac", "damac fc": "damac",
    "al abha": "abha", "al khaleej": "khaleej",
    # Others
    "fc kairat": "fc kairat", "kairat": "fc kairat",
    "qarabag": "qarabag fk", "qarabag fk": "qarabag fk",
    "bodo/glimt": "fk bodøglimt", "bodo glimt": "fk bodøglimt",
    "kobenhavn": "fc copenhagen", "copenhagen": "fc copenhagen",
}

def normalize(s):
    """Remove accents and lowercase"""
    s = unicodedata.normalize('NFD', s)
    s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    return s.lower().strip()

def load_logos():
    """Load all available logos from logos/ folder"""
    logos = {}
    if not os.path.exists("logos"):
        print("WARNING: logos/ folder not found")
        return logos
    
    for league in os.listdir("logos"):
        league_path = os.path.join("logos", league)
        if not os.path.isdir(league_path):
            continue
        for fname in os.listdir(league_path):
            if not fname.endswith('.png'):
                continue
            team_name = fname[:-4]  # Remove .png
            key = normalize(team_name)
            url = f"{LOGOS_URL}/{urllib.parse.quote(league)}/{urllib.parse.quote(fname)}"
            logos[key] = url
    
    print(f"Loaded {len(logos)} logos")
    return logos

def find_logo(team_name, logos, default_logo):
    """Find logo URL for a team"""
    t = team_name.lower().strip()
    tn = normalize(team_name)
    
    # 1. Check aliases first
    if t in ALIASES:
        alias_key = normalize(ALIASES[t])
        if alias_key in logos:
            return logos[alias_key]
    
    # 2. Direct match on normalized name
    if tn in logos:
        return logos[tn]
    
    # 3. Partial match
    for key, url in logos.items():
        if tn in key or key in tn:
            return url
    
    # 4. Try first significant word (if it's not a generic word like a common city)
    forbidden_generic_words = ["madrid", "united", "city", "real", "club", "fase", "liga"]
    words = [w for w in tn.split() if len(w) > 3 and w not in forbidden_generic_words]
    for word in words:
        for key, url in logos.items():
            if word in key:
                return url
    
    return default_logo

def is_excluded(competition):
    """Check if competition should be excluded from main matches"""
    comp_lower = competition.lower()
    return any(excl in comp_lower for excl in EXCLUDED)

def load_channels():
    """Load channel mapping"""
    if os.path.exists('channel_mapping.json'):
        with open('channel_mapping.json', 'r', encoding='utf-8-sig') as f:
            return json.load(f)
    return {}

def parse_eventos():
    """Parse eventos.m3u and generate matches"""
    logos = load_logos()
    channels = load_channels()
    
    main_matches = {}
    other_matches = {}
    today = datetime.now().strftime("%Y-%m-%d")
    default_logo = "https://i.ibb.co/2vhFM7h/soccer-ball-variant.png"
    
    try:
        with open('eventos.m3u', 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except UnicodeDecodeError:
        print("WARNING: utf-8 decode failed, trying latin-1")
        with open('eventos.m3u', 'r', encoding='latin-1') as f:
            lines = f.readlines()
    
    print(f"Processing {len(lines)} lines from eventos.m3u")
    
    for i, line in enumerate(lines):
        line = line.strip()
        if not line.startswith('#EXTINF:'):
            continue
        
        # Extract default logo from line
        logo_match = re.search(r'tvg-logo="([^"]+)"', line)
        line_default = logo_match.group(1) if logo_match else default_logo
        
        # Extract time and title
        title_match = re.search(r',\s*(\d{2}:\d{2})\s+(.+)$', line)
        if not title_match:
            continue
        
        time_str = title_match.group(1)
        title = title_match.group(2)
        
        # Parse "Competition - Home - Away"
        parts = title.split(' - ')
        if len(parts) >= 3:
            competition = parts[0].strip()
            home_team = parts[1].strip()
            away_team = ' - '.join(parts[2:]).strip()
        elif len(parts) == 2:
            competition = parts[0].strip()
            home_team = parts[1].strip()
            away_team = ""
        else:
            competition = parts[0].strip()
            home_team = "Info"
            away_team = ""
        
        # Check next line for acestream link
        if i + 1 >= len(lines):
            continue
        next_line = lines[i + 1].strip()
        if not next_line.startswith('acestream://'):
            continue
        
        acestream_id = next_line.replace('acestream://', '')
        match_key = f"{time_str}|{home_team}|{away_team}"
        
        # Find logos
        home_logo = find_logo(home_team, logos, line_default)
        away_logo = find_logo(away_team, logos, line_default)
        
        # Determine target dict
        target = other_matches if is_excluded(competition) else main_matches
        
        # Get channel name
        channel_name = channels.get(acestream_id, f"Stream {acestream_id[:8]}")
        
        if match_key not in target:
            target[match_key] = {
                'time': time_str,
                'date': today,
                'home_team': home_team,
                'away_team': away_team,
                'home_logo': home_logo,
                'away_logo': away_logo,
                'competition': competition,
                'link': f"acestream://{acestream_id}",
                'channels': [channel_name],
                'links': [{'channel_name': channel_name, 'acestream_id': acestream_id}]
            }
        else:
            target[match_key]['channels'].append(channel_name)
            target[match_key]['links'].append({
                'channel_name': channel_name,
                'acestream_id': acestream_id
            })
    
    return list(main_matches.values()), list(other_matches.values())

def main():
    print("=== Generating matches ===")
    
    main_matches, other_matches = parse_eventos()
    
    # Save matches.json
    with open('matches.json', 'w', encoding='utf-8') as f:
        json.dump(main_matches, f, ensure_ascii=False, indent=2)
    print(f"matches.json: {len(main_matches)} matches")
    
    # Save matches_other.json
    with open('matches_other.json', 'w', encoding='utf-8') as f:
        json.dump(other_matches, f, ensure_ascii=False, indent=2)
    print(f"matches_other.json: {len(other_matches)} matches")
    
    # Stats
    main_with_logos = sum(1 for m in main_matches if 'tvsport/main/logos' in m['home_logo'])
    other_with_logos = sum(1 for m in other_matches if 'tvsport/main/logos' in m['home_logo'])
    print(f"\nLogos stats:")
    print(f"  Main: {main_with_logos}/{len(main_matches)} with real logos")
    print(f"  Other: {other_with_logos}/{len(other_matches)} with real logos")
    
    # Show examples
    print(f"\nMain matches examples:")
    for m in main_matches[:5]:
        print(f"  {m['competition']}: {m['home_team']} vs {m['away_team']}")
    
    print(f"\nOther matches competitions:")
    comps = set(m['competition'] for m in other_matches)
    for c in comps:
        print(f"  - {c}")

if __name__ == "__main__":
    main()