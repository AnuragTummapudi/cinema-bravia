# Artwork and brand assets

The personal screenshots use third-party film and character imagery. Raw movie backdrops, character cutouts and the Doom wallpaper are deliberately **not distributed**. The code builds without them and handles missing art, but it will not reproduce the screenshots until you supply licensed artwork.

## Bring your own artwork

Keep an external folder with these relative directories:

```text
my-artwork/
  home/doctor-doom.jpg
  characters/iron-man.png
  characters/...              # transparent cutouts named by the catalogue
  art/iron-man.jpg
  art/...                     # film backdrops named by the catalogue
```

Use your own artwork or material you have permission to use; the sample filenames describe the personal theme, not a license. `cinema-launcher/catalog.example.json` lists expected paths. You may change those paths and content instead of reproducing the personal catalogue.

Preview the import, then copy matching files into the ignored local asset folders:

```sh
python3 cinema-launcher/scripts/import_artwork.py /path/to/my-artwork --dry-run
python3 cinema-launcher/scripts/import_artwork.py /path/to/my-artwork
```

The importer copies only catalogue-referenced `art/`, `characters/` and `home/` paths. It makes no downloads and never overwrites the catalogue. Existing image files remain unchanged; remove or replace them deliberately if changing a personal theme. Build after importing. Local movie imagery stays Git-ignored.

On an installed TV you can instead push your assets to Cinema's own external-files directory and reload its custom catalogue; see the component README. Prefer 1280×720 backdrops and appropriately sized transparent character PNG/WebP files. The personal Doom source is 3840×2160, decoded at 1920×1080 on the tested BF1; this is not a claim that Cinema renders at 4K.

## Included assets

Geist and Phosphor ship with their license notices. Brand marks remain owned by their respective services; source/treatment records are included. Movie reference URLs in `ARTWORK-SOURCES.md` are provenance, not evidence that redistribution is permitted. Screenshots are documentation of the personal demo, not a reusable stock-art pack.
