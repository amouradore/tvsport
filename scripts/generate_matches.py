#!/usr/bin/env python3
"""
Script pour gÃ©nÃ©rer matches.json avec:
- Logos des Ã©quipes depuis le dossier logos/
- Vrais noms de chaÃ®nes
- Filtrage des compÃ©titions non dÃ©sirÃ©es
"""

import json
import re
import os
import urllib.parse
from datetime import datetime
from collections import defaultdict

# CompÃ©titions Ã  exclure de la page principale (iront dans "other")
EXCLUDED_COMPETITIONS = [
    "Liga Fem", "1RFEF", "Segunda", "ACB", "EHF Europeo",
    "Liga Nacional Juvenil", "Liga Guerreras", "2RFEF",
    "Las Carreras", "Open Australia WTA"
]

# Base URL pour les logos sur GitHub
LOGOS_BASE_URL = "https://raw.githubusercontent.com/amouradore/tvsport/main/logos"

# Mapping des compÃ©titions vers les dossiers de logos
LEAGUE_MAPPING = {
    # Espagne
    "primera": "Spain - LaLiga",
    "laliga": "Spain - LaLiga",
    "la liga": "Spain - LaLiga",
    "liga": "Spain - LaLiga",
    "segunda": "Spain - LaLiga",
    # Angleterre
    "premier": "England - Premier League",
    "premier league": "England - Premier League",
    "epl": "England - Premier League",
    # France
    "ligue 1": "France - Ligue 1",
    "ligue1": "France - Ligue 1",
    # Italie
    "serie a": "Italy - Serie A",
    "seriea": "Italy - Serie A",
    "calcio": "Italy - Serie A",
    # Allemagne
    "bundesliga": "Germany - Bundesliga",
    # Portugal
    "liga portugal": "Portugal - Liga Portugal",
    "primeira liga": "Portugal - Liga Portugal",
    # Pays-Bas
    "eredivisie": "Netherlands - Eredivisie",
    # Belgique
    "jupiler": "Belgium - Jupiler Pro League",
    # Turquie
    "super lig": "TÃ¼rkiye - SÃ¼per Lig",
    "sÃ¼per lig": "TÃ¼rkiye - SÃ¼per Lig",
    # Ecosse
    "scottish": "Scotland - Scottish Premiership",
    # GrÃ¨ce
    "super league": "Greece - Super League 1",
    # Russie
    "premier liga": "Russia - Premier Liga",
    # Ukraine  
    "upl": "Ukraine - Premier Liga",
    # Autriche
    "austria": "Austria - Bundesliga",
    # Suisse
    "swiss": "Switzerland - Super League",
    # Pologne
    "ekstraklasa": "Poland - PKO BP Ekstraklasa",
    # Danemark
    "superliga": "Denmark - Superliga",
    # SuÃ¨de
    "allsvenskan": "Sweden - Allsvenskan",
    # NorvÃ¨ge
    "eliteserien": "Norway - Eliteserien",
    # Croatie
    "hnl": "Croatia - SuperSport HNL",
    # Serbie
    "serbia": "Serbia - Super liga Srbije",
    # Roumanie
    "superliga": "Romania - SuperLiga",
    # Bulgarie
    "efbet": "Bulgaria - efbet Liga",
    # RÃ©publique TchÃ¨que
    "chance liga": "Czech Republic - Chance Liga",
    # IsraÃ«l
    "ligat": "Israel - Ligat ha'Al",
}

# Alias d'Ã©quipes pour amÃ©liorer le matching des logos
TEAM_ALIASES = {
    # Espagne - LaLiga
    "barcelona": "FC Barcelona",
    "barÃ§a": "FC Barcelona",
    "barca": "FC Barcelona",
    "real madrid": "Real Madrid",
    "madrid": "Real Madrid",
    "celta": "Celta de Vigo",
    "atletico": "AtlÃ©tico de Madrid",
    "atletico madrid": "AtlÃ©tico de Madrid",
    "atlÃ©tico": "AtlÃ©tico de Madrid",
    "athletic": "Athletic Bilbao",
    "athletic bilbao": "Athletic Bilbao",
    "athletic club": "Athletic Bilbao",
    "betis": "Real Betis BalompiÃ©",
    "real betis": "Real Betis BalompiÃ©",
    "sevilla": "Sevilla FC",
    "valencia": "Valencia CF",
    "villarreal": "Villarreal CF",
    "getafe": "Getafe CF",
    "osasuna": "CA Osasuna",
    "mallorca": "RCD Mallorca",
    "girona": "Girona FC",
    "alaves": "Deportivo AlavÃ©s",
    "alavÃ©s": "Deportivo AlavÃ©s",
    "espanyol": "RCD Espanyol Barcelona",
    "real sociedad": "Real Sociedad",
    "sociedad": "Real Sociedad",
    "real oviedo": "Real Oviedo",
    "rayo": "Rayo Vallecano",
    "rayo vallecano": "Rayo Vallecano",
    "levante": "Levante UD",
    "elche": "Elche CF",
    
    # Angleterre - Premier League
    "arsenal": "Arsenal FC",
    "manchester utd": "Manchester United",
    "manchester utd.": "Manchester United",
    "man utd": "Manchester United",
    "man united": "Manchester United",
    "manchester united": "Manchester United",
    "liverpool": "Liverpool FC",
    "chelsea": "Chelsea FC",
    "man city": "Manchester City",
    "manchester city": "Manchester City",
    "tottenham": "Tottenham Hotspur",
    "spurs": "Tottenham Hotspur",
    "west ham": "West Ham United",
    "newcastle": "Newcastle United",
    "everton": "Everton FC",
    "aston villa": "Aston Villa",
    "brighton": "Brighton & Hove Albion",
    "crystal palace": "Crystal Palace",
    "brentford": "Brentford FC",
    "fulham": "Fulham FC",
    "wolves": "Wolverhampton Wanderers",
    "wolverhampton": "Wolverhampton Wanderers",
    "bournemouth": "AFC Bournemouth",
    "nottingham": "Nottingham Forest",
    "nottingham forest": "Nottingham Forest",
    "leeds": "Leeds United",
    "burnley": "Burnley FC",
    "sunderland": "Sunderland AFC",
    
    # France - Ligue 1
    "psg": "Paris Saint-Germain",
    "paris": "Paris Saint-Germain",
    "paris saint-germain": "Paris Saint-Germain",
    "marseille": "Olympique Marseille",
    "om": "Olympique Marseille",
    "lyon": "Olympique Lyon",
    "ol": "Olympique Lyon",
    "monaco": "AS Monaco",
    "lille": "LOSC Lille",
    "lens": "RC Lens",
    "rennes": "Stade Rennais FC",
    "nice": "OGC Nice",
    "nantes": "FC Nantes",
    "strasbourg": "RC Strasbourg Alsace",
    "brest": "Stade Brestois 29",
    "toulouse": "FC Toulouse",
    "lorient": "FC Lorient",
    "auxerre": "AJ Auxerre",
    "angers": "Angers SCO",
    "metz": "FC Metz",
    "le havre": "Le Havre AC",
    
    # Italie - Serie A
    "milan": "AC Milan",
    "ac milan": "AC Milan",
    "inter": "Inter Milan",
    "inter milan": "Inter Milan",
    "internazionale": "Inter Milan",
    "juventus": "Juventus FC",
    "juve": "Juventus FC",
    "roma": "AS Roma",
    "lazio": "SS Lazio",
    "napoli": "SSC Napoli",
    "atalanta": "Atalanta BC",
    "fiorentina": "ACF Fiorentina",
    "bologna": "Bologna FC 1909",
    "torino": "Torino FC",
    "udinese": "Udinese Calcio",
    "sassuolo": "US Sassuolo",
    "lecce": "US Lecce",
    "verona": "Hellas Verona",
    "cagliari": "Cagliari Calcio",
    "genoa": "Genoa CFC",
    "parma": "Parma Calcio 1913",
    "como": "Como 1907",
    "cremonese": "US Cremonese",
    
    # Allemagne - Bundesliga
    "bayern": "Bayern Munich",
    "bayern munich": "Bayern Munich",
    "bayern mÃ¼nchen": "Bayern Munich",
    "dortmund": "Borussia Dortmund",
    "borussia dortmund": "Borussia Dortmund",
    "bvb": "Borussia Dortmund",
    "leipzig": "RB Leipzig",
    "rb leipzig": "RB Leipzig",
    "leverkusen": "Bayer 04 Leverkusen",
    "bayer leverkusen": "Bayer 04 Leverkusen",
    "frankfurt": "Eintracht Frankfurt",
    "eintracht frankfurt": "Eintracht Frankfurt",
    "union berlin": "1.FC Union Berlin",
    "freiburg": "SC Freiburg",
    "wolfsburg": "VfL Wolfsburg",
    "stuttgart": "VfB Stuttgart",
    "gladbach": "Borussia MÃ¶nchengladbach",
    "mÃ¶nchengladbach": "Borussia MÃ¶nchengladbach",
    "monchengladbach": "Borussia MÃ¶nchengladbach",
    "koln": "1.FC KÃ¶ln",
    "kÃ¶ln": "1.FC KÃ¶ln",
    "cologne": "1.FC KÃ¶ln",
    "mainz": "1.FSV Mainz 05",
    "hoffenheim": "TSG 1899 Hoffenheim",
    "bremen": "SV Werder Bremen",
    "werder": "SV Werder Bremen",
    "werder bremen": "SV Werder Bremen",
    "augsburg": "FC Augsburg",
    "heidenheim": "1.FC Heidenheim 1846",
    "st. pauli": "FC St. Pauli",
    "st pauli": "FC St. Pauli",
    
    # Portugal - Liga Portugal
    "porto": "FC Porto",
    "fc porto": "FC Porto",
    "benfica": "SL Benfica",
    "sporting": "Sporting CP",
    "sporting lisbon": "Sporting CP",
    "sporting cp": "Sporting CP",
    "braga": "SC Braga",
    "guimaraes": "VitÃ³ria GuimarÃ£es SC",
    "vitoria guimaraes": "VitÃ³ria GuimarÃ£es SC",
    "famalicao": "FC FamalicÃ£o",
    "gil vicente": "Gil Vicente FC",
    "arouca": "FC Arouca",
    "estoril": "GD Estoril Praia",
    "casa pia": "Casa Pia AC",
    "estrela": "CF Estrela Amadora",
    "santa clara": "CD Santa Clara",
    "nacional": "CD Nacional",
    "rio ave": "Rio Ave FC",
    "moreirense": "Moreirense FC",
    "tondela": "CD Tondela",
    "alverca": "FC Alverca",
}

def load_logos_mapping():
    """Charge le mapping des logos depuis le dossier logos/"""
    logos_mapping = {}
    logos_dir = "logos"
    
    if not os.path.exists(logos_dir):
        print(f"âš ï¸ Dossier logos/ non trouvÃ©")
        return logos_mapping
    
    for league_dir in os.listdir(logos_dir):
        league_path = os.path.join(logos_dir, league_dir)
        if os.path.isdir(league_path):
            for logo_file in os.listdir(league_path):
                if logo_file.endswith('.png'):
                    team_name = logo_file.replace('.png', '')
                    team_name_lower = team_name.lower()
                    # URL encodÃ©e pour GitHub
                    encoded_league = urllib.parse.quote(league_dir, safe="")
                    encoded_file = urllib.parse.quote(logo_file, safe="")
                    logo_url = f"{LOGOS_BASE_URL}/{encoded_league}/{encoded_file}"
                    logos_mapping[team_name_lower] = {
                        'url': logo_url,
                        'original_name': team_name,
                        'league': league_dir
                    }
    
    print(f"âœ… ChargÃ© {len(logos_mapping)} logos")
    return logos_mapping

def get_league_folder(competition):
    """Trouve le dossier de la ligue correspondant Ã  la compÃ©tition"""
    comp_lower = competition.lower().strip()
    
    for key, folder in LEAGUE_MAPPING.items():
        if key in comp_lower:
            return folder
    
    return None

def get_team_logo(team_name, competition, logos_mapping, default_logo):
    """Trouve le logo d'une Ã©quipe"""
    team_lower = team_name.lower().strip()
    
    # 1. Chercher dans les alias
    if team_lower in TEAM_ALIASES:
        alias_name = TEAM_ALIASES[team_lower].lower()
        if alias_name in logos_mapping:
            return logos_mapping[alias_name]['url']
    
    # 2. Chercher correspondance exacte
    if team_lower in logos_mapping:
        return logos_mapping[team_lower]['url']
    
    # 3. Chercher correspondance partielle
    for key, data in logos_mapping.items():
        if team_lower in key or key in team_lower:
            return data['url']
        # Aussi vÃ©rifier le nom original
        if team_lower in data['original_name'].lower():
            return data['url']
    
    # 4. Chercher par ligue spÃ©cifique
    league_folder = get_league_folder(competition)
    if league_folder:
        for key, data in logos_mapping.items():
            if data['league'] == league_folder:
                if team_lower in key or key in team_lower or team_lower in data['original_name'].lower():
                    return data['url']
    
    return default_logo

def load_channel_mapping():
    """Charge le mapping des chaÃ®nes"""
    channel_mapping = {}
    try:
        with open('channel_mapping.json', 'r', encoding='utf-8') as f:
            channel_mapping = json.load(f)
        print(f"âœ… ChargÃ© {len(channel_mapping)} chaÃ®nes")
    except Exception as e:
        print(f"âš ï¸ Erreur chargement channel_mapping.json: {e}")
    return channel_mapping

def is_excluded_competition(competition):
    """VÃ©rifie si une compÃ©tition doit Ãªtre exclue"""
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
                                home_logo = get_team_logo(home_team, competition, logos_mapping, default_logo)
                                away_logo = get_team_logo(away_team, competition, logos_mapping, default_logo)
                                
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
    print("ðŸ”„ GÃ©nÃ©ration des matches...")
    
    main_matches, other_matches = parse_eventos()
    
    with open('matches.json', 'w', encoding='utf-8') as f:
        json.dump(main_matches, f, ensure_ascii=False, indent=2)
    print(f"âœ… matches.json: {len(main_matches)} matches")
    
    with open('matches_other.json', 'w', encoding='utf-8') as f:
        json.dump(other_matches, f, ensure_ascii=False, indent=2)
    print(f"âœ… matches_other.json: {len(other_matches)} matches")
    
    # Stats
    real_logos = sum(1 for m in main_matches if 'tvsport/main/logos' in m['home_logo'])
    print(f"\nðŸ“Š Statistiques: Logos rÃ©els: {real_logos}/{len(main_matches)}")
    
    # Afficher quelques exemples
    print(f"\nðŸ“‹ Exemples:")
    for m in main_matches[:3]:
        print(f"  {m['home_team']} vs {m['away_team']} ({m['competition']})")
        print(f"    Home: {m['home_logo'][:80]}...")

if __name__ == "__main__":
    main()