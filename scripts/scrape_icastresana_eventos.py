import requests
import json
import re
from datetime import datetime
from collections import defaultdict

def get_team_logo(team_name):
    '''Récupérer le vrai logo d'une équipe via TheSportsDB API'''
    try:
        # Nettoyer le nom de l'équipe
        clean_name = team_name.strip()
        url = f"https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t={clean_name}"
        response = requests.get(url, timeout=5)
        data = response.json()
        
        if data.get('teams') and len(data['teams']) > 0:
            team = data['teams'][0]
            logo = team.get('strTeamBadge') or team.get('strTeamLogo')
            if logo:
                return logo
    except:
        pass
    
    # Logo par défaut (ballon de foot)
    return "https://i.ibb.co/2vhFM7h/soccer-ball-variant.png"

def parse_icastresana_eventos():
    '''Parse le fichier eventos.m3u d'Icastresana'''
    eventos_url = "https://raw.githubusercontent.com/Icastresana/lista1/main/eventos.m3u"
    
    try:
        response = requests.get(eventos_url, timeout=10)
        response.raise_for_status()
        content = response.text
        
        lines = content.split('\n')
        i = 0
        today = datetime.now().strftime("%Y-%m-%d")
        
        # Dictionnaire pour regrouper les matches identiques
        matches_dict = defaultdict(lambda: {
            'time': '',
            'date': today,
            'home_team': '',
            'away_team': '',
            'home_logo': '',
            'away_logo': '',
            'competition': '',
            'links': []
        })
        
        while i < len(lines):
            line = lines[i].strip()
            
            if line.startswith('#EXTINF:'):
                # Extraire les informations
                logo_match = re.search(r'tvg-logo="([^"]+)"', line)
                logo_url = logo_match.group(1) if logo_match else ""
                
                comma_split = line.split(',', 1)
                if len(comma_split) > 1:
                    info = comma_split[1].strip()
                    
                    # Extraire l'heure
                    time_match = re.match(r'(\d{2}:\d{2})', info)
                    time_str = time_match.group(1) if time_match else "00:00"
                    
                    # Extraire le reste
                    title = info[len(time_str):].strip() if time_match else info
                    
                    # Chercher "Competition - Team1 - Team2"
                    if ' - ' in title:
                        parts = title.split(' - ')
                        if len(parts) >= 2:
                            away_team = parts[-1].strip()
                            before = ' - '.join(parts[:-1])
                            
                            comp_match = re.match(r'^([^-]+?)\s+-\s+(.+)$', before)
                            if comp_match:
                                competition = comp_match.group(1).strip()
                                home_team = comp_match.group(2).strip()
                            else:
                                competition = ""
                                home_team = before.strip()
                            
                            # Ligne suivante = acestream
                            if i + 1 < len(lines):
                                next_line = lines[i + 1].strip()
                                if next_line.startswith('acestream://'):
                                    acestream_id = next_line.replace('acestream://', '')
                                    
                                    # Clé unique pour le match
                                    match_key = f"{time_str}_{home_team}_{away_team}"
                                    
                                    # Extraire le nom de la chaîne depuis l'EXTINF
                                    channel_name = "AceStream"
                                    group_match = re.search(r'group-title="([^"]+)"', line)
                                    if group_match:
                                        channel_name = group_match.group(1)
                                    else:
                                        # Essayer d'extraire depuis le titre après la compétition
                                        name_match = re.search(r'\(([^)]+)\)', title)
                                        if name_match:
                                            channel_name = name_match.group(1)
                                    
                                    # Ajouter/mettre à jour le match
                                    match_data = matches_dict[match_key]
                                    if not match_data['time']:
                                        match_data['time'] = time_str
                                        match_data['home_team'] = home_team
                                        match_data['away_team'] = away_team
                                        match_data['competition'] = competition
                                    
                                    # Ajouter le lien
                                    match_data['links'].append({
                                        'channel_name': channel_name,
                                        'acestream_id': acestream_id
                                    })
                                    
                                    i += 2
                                    continue
            i += 1
        
        # Convertir en liste et récupérer les logos
        matches = []
        print(f"Récupération des logos pour {len(matches_dict)} matches...")
        
        for idx, (key, match_data) in enumerate(matches_dict.items()):
            # Récupérer les logos des équipes
            if idx < 20:  # Limiter à 20 pour ne pas surcharger l'API
                home_logo = get_team_logo(match_data['home_team'])
                away_logo = get_team_logo(match_data['away_team'])
            else:
                home_logo = "https://i.ibb.co/2vhFM7h/soccer-ball-variant.png"
                away_logo = "https://i.ibb.co/2vhFM7h/soccer-ball-variant.png"
            
            match_data['home_logo'] = home_logo
            match_data['away_logo'] = away_logo
            match_data['date'] = today
            
            # Ajouter link par défaut (premier lien) pour compatibilité
            if match_data['links']:
                match_data['link'] = f"acestream://{match_data['links'][0]['acestream_id']}"
                match_data['channels'] = [link['channel_name'] for link in match_data['links']]
            
            matches.append(match_data)
        
        # Trier par priorité d'équipes
        priority_teams = [
            'Real Madrid', 'FC Barcelona', 'Barcelona', 'Barça',
            'Manchester United', 'Paris Saint-Germain', 'PSG',
            'Manchester City', 'Juventus', 'Chelsea', 'Liverpool',
            'Bayern Munich', 'Arsenal', 'Al-Nassr', 'Al-Ahly', 'Al-Hilal'
        ]
        
        def get_priority(match):
            home = match['home_team'].lower()
            away = match['away_team'].lower()
            
            for idx, team in enumerate(priority_teams):
                team_lower = team.lower()
                if team_lower in home or team_lower in away:
                    return idx
            return 999  # Équipes non prioritaires à la fin
        
        matches.sort(key=lambda m: (get_priority(m), m['time']))
        
        return matches
    
    except Exception as e:
        print(f"Erreur: {e}")
        import traceback
        traceback.print_exc()
        return []

def main():
    print("Parsing des événements Icastresana...")
    matches = parse_icastresana_eventos()
    
    with open("matches.json", "w", encoding="utf-8") as f:
        json.dump(matches, f, ensure_ascii=False, indent=2)
    
    print(f"OK: {len(matches)} matches uniques sauvegardés")
    
    for i, match in enumerate(matches[:10]):
        print(f"{i+1}. {match['time']} - {match['home_team']} vs {match['away_team']} ({len(match['links'])} liens)")

if __name__ == "__main__":
    main()
