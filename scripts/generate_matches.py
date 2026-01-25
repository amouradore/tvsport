#!/usr/bin/env python3
"""
Script pour générer matches.json avec:
- Logos des équipes depuis le dossier logos/
- Vrais noms de chaînes
- Filtrage des compétitions non désirées
"""

import json
import re
import os
import urllib.parse
from datetime import datetime
from collections import defaultdict

# Compétitions à exclure de la page principale (iront dans "other")
EXCLUDED_COMPETITIONS = [
    "Liga Fem", "1RFEF", "Segunda", "ACB", "EHF Europeo",
    "Liga Nacional Juvenil", "Liga Guerreras", "2RFEF",
    "Las Carreras", "Open Australia WTA"
]

# Base URL pour les logos sur GitHub
LOGOS_BASE_URL = "https://raw.githubusercontent.com/amouradore/tvsport/main/logos"

# Alias d'équipes pour améliorer le matching des logos
TEAM_ALIASES = {
    # Espagne - LaLiga
    "barcelona": "fc barcelona",
    "barça": "fc barcelona",
    "barca": "fc barcelona",
    "real madrid": "real madrid",
    "madrid": "real madrid",
    "celta": "celta de vigo",
    "atletico": "atlético de madrid",
    "atletico madrid": "atlético de madrid",
    "atlético": "atlético de madrid",
    "athletic": "athletic bilbao",
    "athletic bilbao": "athletic bilbao",
    "athletic club": "athletic bilbao",
    "betis": "real betis balompié",
    "real betis": "real betis balompié",
    "sevilla": "sevilla fc",
    "valencia": "valencia cf",
    "villarreal": "villarreal cf",
    "getafe": "getafe cf",
    "osasuna": "ca osasuna",
    "mallorca": "rcd mallorca",
    "girona": "girona fc",
    "alaves": "deportivo alavés",
    "alavés": "deportivo alavés",
    "espanyol": "rcd espanyol barcelona",
    "real sociedad": "real sociedad",
    "real oviedo": "real oviedo",
    "rayo": "rayo vallecano",
    "rayo vallecano": "rayo vallecano",
    "levante": "levante ud",
    "elche": "elche cf",
    
    # Angleterre - Premier League
    "arsenal": "arsenal fc",
    "manchester utd": "manchester united",
    "manchester utd.": "manchester united",
    "man utd": "manchester united",
    "man united": "manchester united",
    "liverpool": "liverpool fc",
    "chelsea": "chelsea fc",
    "man city": "manchester city",
    "manchester city": "manchester city",
    "tottenham": "tottenham hotspur",
    "spurs": "tottenham hotspur",
    "west ham": "west ham united",
    "newcastle": "newcastle united",
    "everton": "everton fc",
    "aston villa": "aston villa",
    "brighton": "brighton & hove albion",
    "crystal palace": "crystal palace",
    "brentford": "brentford fc",
    "fulham": "fulham fc",
    "wolves": "wolverhampton wanderers",
    "wolverhampton": "wolverhampton wanderers",
    "bournemouth": "afc bournemouth",
    "nottingham": "nottingham forest",
    "leeds": "leeds united",
    "burnley": "burnley fc",
    "sunderland": "sunderland afc",
    
    # France - Ligue 1
    "psg": "paris saint-germain",
    "paris": "paris saint-germain",
    "marseille": "olympique marseille",
    "om": "olympique marseille",
    "lyon": "olympique lyon",
    "ol": "olympique lyon",
    "monaco": "as monaco",
    "lille": "losc lille",
    "lens": "rc lens",
    "rennes": "stade rennais fc",
    "nice": "ogc nice",
    "nantes": "fc nantes",
    "strasbourg": "rc strasbourg alsace",
    "brest": "stade brestois 29",
    "toulouse": "fc toulouse",
    "lorient": "fc lorient",
    "auxerre": "aj auxerre",
    "angers": "angers sco",
    "metz": "fc metz",
    "le havre": "le havre ac",
    
    # Italie - Serie A
    "milan": "ac milan",
    "ac milan": "ac milan",
    "inter": "inter milan",
    "inter milan": "inter milan",
    "juventus": "juventus fc",
    "juve": "juventus fc",
    "roma": "as roma",
    "lazio": "ss lazio",
    "napoli": "ssc napoli",
    "atalanta": "atalanta bc",
    "fiorentina": "acf fiorentina",
    "bologna": "bologna fc 1909",
    "torino": "torino fc",
    "udinese": "udinese calcio",
    "sassuolo": "us sassuolo",
    "lecce": "us lecce",
    "verona": "hellas verona",
    "cagliari": "cagliari calcio",
    "genoa": "genoa cfc",
    "parma": "parma calcio 1913",
    "como": "como 1907",
    "cremonese": "us cremonese",
    
    # Allemagne - Bundesliga
    "bayern": "bayern munich",
    "bayern munich": "bayern munich",
    "dortmund": "borussia dortmund",
    "bvb": "borussia dortmund",
    "leipzig": "rb leipzig",
    "leverkusen": "bayer 04 leverkusen",
    "bayer leverkusen": "bayer 04 leverkusen",
    "frankfurt": "eintracht frankfurt",
    "union berlin": "1.fc union berlin",
    "freiburg": "sc freiburg",
    "wolfsburg": "vfl wolfsburg",
    "stuttgart": "vfb stuttgart",
    "gladbach": "borussia mönchengladbach",
    "mönchengladbach": "borussia mönchengladbach",
    "koln": "1.fc köln",
    "köln": "1.fc köln",
    "cologne": "1.fc köln",
    "mainz": "1.fsv mainz 05",
    "hoffenheim": "tsg 1899 hoffenheim",
    "bremen": "sv werder bremen",
    "werder": "sv werder bremen",
    "augsburg": "fc augsburg",
    "heidenheim": "1.fc heidenheim 1846",
    "st. pauli": "fc st. pauli",
    
    # Portugal - Liga Portugal
    "porto": "fc porto",
    "benfica": "sl benfica",
    "sporting": "sporting cp",
    "sporting lisbon": "sporting cp",
    "braga": "sc braga",
    "guimaraes": "vitória guimarães sc",
    "vitoria guimaraes": "vitória guimarães sc",
    "famalicao": "fc famalicão",
    "gil vicente": "gil vicente fc",
    "arouca": "fc arouca",
    "estoril": "gd estoril praia",
    "casa pia": "casa pia ac",
    "estrela": "cf estrela amadora",
    "santa clara": "cd santa clara",
    "nacional": "cd nacional",
    "rio ave": "rio ave fc",
    "moreirense": "moreirense fc",
    "tondela": "cd tondela",
    "alverca": "fc alverca",
}

def load_logos_mapping():
    """Charge le mapping des logos depuis le dossier logos/"""
    logos_mapping = {}
    logos_dir = "logos"
    
    if not os.path.exists(logos_dir):
        print(f"⚠️ Dossier logos/ non trouvé")
        return logos_mapping
    
    for league_dir in os.listdir(logos_dir):
        league_path = os.path.join(logos_dir, league_dir)
        if os.path.isdir(league_path):
            for logo_file in os.listdir(league_path):
                if logo_file.endswith('.png'):
                    team_name = logo_file.replace('.png', '').lower()
                    # URL encodée pour GitHub
                    encoded_league = urllib.parse.quote(league_dir)
                    encoded_file = urllib.parse.quote(logo_file)
                    logo_url = f"{LOGOS_BASE_URL}/{encoded_league}/{encoded_file}"
                    logos_mapping[team_name] = logo_url
    
    print(f"✅ Chargé {len(logos_mapping)} logos")
    return logos_mapping

def normalize_team_name(team_name):
    """Normalise un nom d'équipe"""
    team_lower = team_name.lower().strip()
    
    # Vérifier d'abord dans les alias
    if team_lower in TEAM_ALIASES:
        return TEAM_ALIASES[team_lower]
    
    # Nettoyer le nom (enlever les suffixes communs)
    for suffix in [' fc', ' cf', ' sc', ' ac', ' bc', ' if', ' bk', ' fk', ' nk', ' afc', ' cfc']:
        if team_lower.endswith(suffix):
            base_name = team_lower[:-len(suffix)].strip()
            if base_name in TEAM_ALIASES:
                return TEAM_ALIASES[base_name]
    
    return team_lower

def get_team_logo(team_name, logos_mapping, default_logo):
    """Trouve le logo d'une équipe"""
    normalized_name = normalize_team_name(team_name)
    
    # Correspondance exacte
    if normalized_name in logos_mapping:
        return logos_mapping[normalized_name]
    
    # Correspondance partielle
    for key, logo in logos_mapping.items():
        if normalized_name in key or key in normalized_name:
            return logo
    
    # Essayer avec le nom original
    team_lower = team_name.lower().strip()
    if team_lower in logos_mapping:
        return logos_mapping[team_lower]
    
    for key, logo in logos_mapping.items():
        if team_lower in key or key in team_lower:
            return logo
    
    return default_logo

def load_channel_mapping():
    """Charge le mapping des chaînes"""
    channel_mapping = {}
    try:
        with open('channel_mapping.json', 'r', encoding='utf-8') as f:
            channel_mapping = json.load(f)
        print(f"✅ Chargé {len(channel_mapping)} chaînes")
    except Exception as e:
        print(f"⚠️ Erreur chargement channel_mapping.json: {e}")
    return channel_mapping

def is_excluded_competition(competition):
    """Vérifie si une compétition doit être exclue"""
    comp_lower = competition.lower()
    for excluded in EXCLUDED_COMPETITIONS:
        if excluded.lower() in comp_lower:
            return True
    return False

def parse_eventos():
    """Parse le fichier eventos.m3u"""
    logos_mapping = load_logos_mapping()
    channel_mapping = load_channel_mapping()
    
    main_matches = defaultdict(lambda: {
        'time': '', 'date': '', 'home_team': '', 'away_team': '',
        'home_logo': '', 'away_logo': '', 'competition': '',
        'channels': [], 'links': [], 'link': ''
    })
    
    other_matches = defaultdict(lambda: {
        'time': '', 'date': '', 'home_team': '', 'away_team': '',
        'home_logo': '', 'away_logo': '', 'competition': '',
        'channels': [], 'links': [], 'link': ''
    })
    
    today = datetime.now().strftime("%Y-%m-%d")
    
    with open('eventos.m3u', 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    for i in range(len(lines)):
        line = lines[i].strip()
        if line.startswith('#EXTINF:'):
            logo_match = re.search(r'tvg-logo="([^"]+)"', line)
            default_logo = logo_match.group(1) if logo_match else "https://i.ibb.co/2vhFM7h/soccer-ball-variant.png"
            
            title_match = re.search(r',\s*(\d{2}:\d{2})\s+(.+)$', line)
            if title_match:
                time_str = title_match.group(1)
                title = title_match.group(2)
                
                teams_match = re.match(r'(.+?)\s+-\s+(.+?)\s+-\s+(.+)$', title)
                if teams_match:
                    competition = teams_match.group(1).strip()
                    home_team = teams_match.group(2).strip()
                    away_team = teams_match.group(3).strip()
                    
                    if i + 1 < len(lines):
                        next_line = lines[i + 1].strip()
                        if next_line.startswith('acestream://'):
                            acestream_id = next_line.replace('acestream://', '')
                            match_key = f"{time_str}|{home_team}|{away_team}"
                            
                            channel_name = channel_mapping.get(acestream_id, f"Stream {acestream_id[:8]}")
                            
                            if is_excluded_competition(competition):
                                matches_dict = other_matches
                            else:
                                matches_dict = main_matches
                            
                            match_data = matches_dict[match_key]
                            if not match_data['time']:
                                home_logo = get_team_logo(home_team, logos_mapping, default_logo)
                                away_logo = get_team_logo(away_team, logos_mapping, default_logo)
                                
                                match_data.update({
                                    'time': time_str, 'date': today,
                                    'home_team': home_team, 'away_team': away_team,
                                    'home_logo': home_logo, 'away_logo': away_logo,
                                    'competition': competition,
                                    'link': f"acestream://{acestream_id}"
                                })
                            
                            match_data['links'].append({
                                'channel_name': channel_name,
                                'acestream_id': acestream_id
                            })
                            match_data['channels'].append(channel_name)
    
    return list(main_matches.values()), list(other_matches.values())

def main():
    print("🔄 Génération des matches...")
    
    main_matches, other_matches = parse_eventos()
    
    with open('matches.json', 'w', encoding='utf-8') as f:
        json.dump(main_matches, f, ensure_ascii=False, indent=2)
    print(f"✅ matches.json: {len(main_matches)} matches")
    
    with open('matches_other.json', 'w', encoding='utf-8') as f:
        json.dump(other_matches, f, ensure_ascii=False, indent=2)
    print(f"✅ matches_other.json: {len(other_matches)} matches")
    
    real_logos = sum(1 for m in main_matches if 'tvsport/main/logos' in m['home_logo'])
    print(f"\n📊 Statistiques: Logos réels: {real_logos}/{len(main_matches)}")

if __name__ == "__main__":
    main()
