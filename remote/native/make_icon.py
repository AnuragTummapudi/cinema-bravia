"""Render the project's authored vector remote symbol; no generated imagery."""
from pathlib import Path
import json
from PIL import Image,ImageDraw,PngImagePlugin
root=Path(__file__).resolve().parents[1];out=root/'native/AppIcon.iconset';out.mkdir(exist_ok=True)
svg='''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"><rect x="32" y="32" width="960" height="960" rx="216" fill="#181818"/><rect x="300" y="144" width="424" height="736" rx="152" fill="#313131"/><circle cx="512" cy="432" r="144" fill="#1f1f1f"/><circle cx="512" cy="432" r="56" fill="#ecebe5"/><g fill="none" stroke="#ecebe5" stroke-width="18" stroke-linecap="round" stroke-linejoin="round"><path d="m488 332 24-24 24 24m76 76 24 24-24 24m-76 76-24 24-24-24m-76-76-24-24 24-24"/></g><rect x="424" y="656" width="176" height="24" rx="12" fill="#ecebe5"/></svg>'''
(root/'static/icon.svg').write_text(svg+'\n')
scale=4;im=Image.new('RGBA',(1024*scale,1024*scale));d=ImageDraw.Draw(im)
def box(b):return tuple(int(v*scale) for v in b)
def rect(b,r,fill):d.rounded_rectangle(box(b),int(r*scale),fill=fill)
def circle(cx,cy,r,fill):d.ellipse(box((cx-r,cy-r,cx+r,cy+r)),fill=fill)
rect((32,32,992,992),216,'#181818');rect((300,144,724,880),152,'#313131');circle(512,432,144,'#1f1f1f');circle(512,432,56,'#ecebe5')
for points in [[(488,332),(512,308),(536,332)],[(612,408),(636,432),(612,456)],[(536,532),(512,556),(488,532)],[(412,456),(388,432),(412,408)]]:
 d.line([(x*scale,y*scale) for x,y in points],fill='#ecebe5',width=18*scale,joint='curve')
 for x,y in [points[0],points[-1]]:circle(x,y,9,'#ecebe5')
rect((424,656,600,680),12,'#ecebe5')
im=im.resize((1024,1024),Image.Resampling.LANCZOS);meta=PngImagePlugin.PngInfo();meta.add_text('impeccable:prompt','Original project vector geometry in remote/static/icon.svg, rendered by remote/native/make_icon.py. Graphite rounded square, minimal remote silhouette, white D-pad. No AI-generated imagery.');meta.add_text('Origin','Original project vector icon; rendered by remote/native/make_icon.py from static/icon.svg geometry')
for size in [16,32,128,256,512]:
 im.resize((size,size),Image.Resampling.LANCZOS).save(out/f'icon_{size}x{size}.png',pnginfo=meta)
 im.resize((size*2,size*2),Image.Resampling.LANCZOS).save(out/f'icon_{size}x{size}@2x.png',pnginfo=meta)
for size in [192,512]:im.resize((size,size),Image.Resampling.LANCZOS).save(root/f'static/app-icon-{size}.png',pnginfo=meta)
(root/'static/manifest.webmanifest').write_text(json.dumps({'name':'BRAVIA Remote','short_name':'BRAVIA','start_url':'/','display':'standalone','background_color':'#181818','theme_color':'#181818','icons':[{'src':'/app-icon-192.png','sizes':'192x192','type':'image/png'},{'src':'/app-icon-512.png','sizes':'512x512','type':'image/png'},{'src':'/icon.svg','sizes':'any','type':'image/svg+xml'}]},separators=(',',':'))+'\n')
print('Original vector and complete Mac iconset rendered')
