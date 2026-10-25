#!/usr/bin/env python3
"""Validate owner-edited content before transferring it to the TV."""
import json,pathlib,re,sys,urllib.parse

def validate(path):
 p=pathlib.Path(path)
 if p.stat().st_size>512*1024:raise ValueError('Catalog exceeds 512 KiB')
 c=json.loads(p.read_text());cols=c['collections'];films=c['movies'];dock=c['dock']
 if not 1<=len(cols)<=5 or len(set(cols))!=len(cols):raise ValueError('Use 1-5 unique collections')
 if not 1<=len(films)<=100 or len(dock)>5:raise ValueError('Use 1-100 films and at most five dock apps')
 seen=set()
 for col in cols:
  if not isinstance(col,str) or not 1<=len(col)<=20:raise ValueError('Collection name is invalid')
  if not any(m['collection']==col for m in films):raise ValueError('Every collection needs a film')
 for m in films:
  if m['id'] in seen or m['collection'] not in cols:raise ValueError('Duplicate film or unknown collection')
  seen.add(m['id'])
  if not re.fullmatch(r'art/[a-zA-Z0-9_-]+\.(jpg|png|webp)',m['art']):raise ValueError('Unsafe artwork path')
  u=urllib.parse.urlparse(m['uri'])
  if u.scheme!='https' or not u.hostname or u.username:raise ValueError('Use HTTPS movie destinations')
  if re.search('factory|facmenu|diagnos|servicemode|productiontest',m['package'],re.I):raise ValueError('Unsafe provider package')
  for field in ['title','character','year','runtime','summary','actionLabel','package']:
   if not isinstance(m[field],str) or not m[field]:raise ValueError('Missing '+field)
 for m in films:
  if m.get('characterArt') and not re.fullmatch(r'characters/[a-zA-Z0-9_-]+\.(png|webp)',m['characterArt']):raise ValueError('Unsafe character path')
 if c.get('homeArtwork') and not re.fullmatch(r'home/[a-zA-Z0-9_-]+\.(jpg|png|webp)',c['homeArtwork']):raise ValueError('Unsafe home artwork path')
 favorites=c.get('favorites',[])
 if len(favorites)>7 or len(set(favorites))!=len(favorites) or any(i not in seen for i in favorites):raise ValueError('Invalid favourites')
 return len(cols),len(films)
if __name__=='__main__':
 try:
  a,b=validate(sys.argv[1]);print(f'Valid catalog: {a} collections, {b} films')
 except (ValueError,KeyError,TypeError,OSError) as e:raise SystemExit(str(e))
