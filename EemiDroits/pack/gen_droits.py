"""Menus de /droits (EemiDroits) au style des menus du lobby (EemiCore/pack/gen_nav.py, style Refuge).

Demande de Thibault (15/11/2026) : « tu peux en faire une interface ? » -> même rendu que le navigateur /jeux.
Le dessin (couleurs, boutons, panneau, petites capitales) est importé de gen_nav.py : si le style du lobby change,
relancer ce script après gen_nav.py suffit.

Fichiers ajoutés au pack (rien d'autre n'est touché, et gen_nav.py ne les retire pas) :
  assets/eemicraft/font/droits.json        fonds + calques + espaces (glyphes U+E000.., U+E400..)
  assets/eemicraft/font/dt<y>.json          textes dynamiques (une police par hauteur)
  assets/eemicraft/textures/font/droits/   images
L'objet invisible des boutons (`eemicraft:nav/vide`) est celui de gen_nav.py : il doit être dans le pack.

Usage : python gen_droits.py <pack d'entrée.zip> <sortie.zip>
Écrit aussi ../src/main/java/fr/eemicraft/droits/ui/Fonds.java (généré) et ../apercus/menus.png.
"""
import hashlib
import io
import json
import os
import sys
import zipfile

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
RACINE = os.path.dirname(ICI)
sys.path.insert(0, os.path.join(os.path.dirname(RACINE), "EemiCore", "pack"))
import gen_nav as gn  # noqa: E402

NS = "eemicraft"
FONT = "droits"
JAVA = os.path.join(RACINE, "src", "main", "java", "fr", "eemicraft", "droits", "ui", "Fonds.java")


def reserve(name):
    if name.startswith(f"assets/{NS}/font/dt") and name.endswith(".json"):
        return True
    return name == f"assets/{NS}/font/{FONT}.json" or name.startswith(f"assets/{NS}/textures/font/{FONT}/")


btn, tile, row_y, cx = gn.btn, gn.tile, gn.row_y, gn.cx
ACC = gn.ACC
L = gn.LIST_CELLS


def liste(mid, titre, kinds):
    el = list(gn.HEAD) + [("cells", "liste", L)] + list(gn.PAGER)
    ov = [(k, "liste", i) for i in range(len(L)) for k in kinds]
    return dict(id=mid, titre=titre, rows=6, el=el, overlays=ov)


DROITS_CELLS = [(c, r) for r in (2, 3) for c in range(9)]


def menus():
    m = []
    el = list(gn.HEAD) + [
        btn("joueurs", 0, 1, 3, 2, "joueurs", "JOUEURS", ACC["equipes"], status=True),
        btn("jeux", 3, 1, 3, 2, "hasard_de", "JEUX", ACC["tnt"], status=True),
        btn("betas", 6, 1, 3, 2, "quete_ok", "BÊTA", ACC["ambiance"], status=True),
        btn("interdits", 3, 3, 3, 2, "aucun", "INTERDITS", ACC["combat"], status=True),
        ("text", "bas", 88, row_y(5) + 7, "c", 160),
    ]
    m.append(dict(id="droits", titre="DROITS", rows=6, el=el, overlays=[]))
    m.append(liste("droits_joueurs", "JOUEURS", ("chef", "non")))
    m.append(liste("droits_betas", "BÊTA-TESTEURS", ("chef",)))
    m.append(liste("droits_interdits", "INTERDITS", ("non",)))
    el = list(gn.HEAD) + [
        btn("beta", 0, 1, 3, 1, "quete_ok", "BÊTA"),
        ("cells", "tete", [(4, 1)]),
        btn("groupes", 6, 1, 3, 1, "groupe", "GROUPES"),
        ("cells", "droits", DROITS_CELLS),
        btn("mondes", 0, 4, 3, 1, "cat_iles", "MONDES"),
        btn("jeux", 3, 4, 3, 1, "hasard_de", "JEUX"),
        ("text", "resume", cx(7), row_y(4) + 7, "c", 50),
        btn("retour", 0, 5, 1, 1, "retour"),
        ("text", "bas", 96, row_y(5) + 7, "c", 140),
    ]
    ov = [("sel", "beta"), ("off", "groupes")] + [(k, "droits", i) for i in range(len(DROITS_CELLS))
                                                  for k in ("ok", "non", "chef")]
    m.append(dict(id="droits_fiche", titre="FICHE", rows=6, el=el, overlays=ov))
    m.append(liste("droits_groupes", "GROUPES", ("ok",)))
    m.append(liste("droits_mondes", "MONDES", ("non", "chef")))
    m.append(liste("droits_jeuxj", "JEUX INTERDITS", ("non",)))
    m.append(liste("droits_jeux", "ÉTAT DES JEUX", ("chef", "non")))
    return m


MENUS = menus()


def build_files(v54, ic):
    gn.MENUS = MENUS
    bases, overlays, layouts = gn.build_menus(v54, ic)
    files = {}
    providers = [{"type": "space", "advances": {chr(c): v for c, v in gn.NEG + gn.POS}}]
    glyphs = {}
    for i, (mid, img) in enumerate(bases.items()):
        ch = chr(0xE000 + i)
        glyphs["bg:" + mid] = ch
        files[f"assets/{NS}/textures/font/{FONT}/bg_{mid}.png"] = gn.png_bytes(img)
        providers.append({"type": "bitmap", "file": f"{NS}:font/{FONT}/bg_{mid}.png", "ascent": gn.ASCENT_BG,
                          "height": 256, "chars": [ch]})
    for i, (oid, img) in enumerate(overlays.items()):
        ch = chr(0xE400 + i)
        glyphs["ov:" + oid] = ch
        files[f"assets/{NS}/textures/font/{FONT}/ov_{oid.replace(':', '_')}.png"] = gn.png_bytes(img)
        providers.append({"type": "bitmap", "file": f"{NS}:font/{FONT}/ov_{oid.replace(':', '_')}.png",
                          "ascent": gn.ASCENT_BG, "height": 256, "chars": [ch]})
    files[f"assets/{NS}/font/{FONT}.json"] = json.dumps({"providers": providers}, indent=1, ensure_ascii=True).encode()
    # 26/11 : police lisse Inter de gen_nav.py (eemicraft:nav/inter.ttf et ses symboles, déjà dans le pack)
    yks = {(t[1], k) for lay in layouts.values() for t in lay["texts"].values() for k in range(len(gn.TAILLES))} \
        | {(lb[2], lb[5]) for lay in layouts.values() for lb in lay["labels"]}
    ys = sorted({y for y, _ in yks})
    files.update(gn.text_fonts("dt", yks, f"{NS}:font/nav/symboles.png"))
    return files, dict(bases=bases, overlays=overlays, layouts=layouts, glyphs=glyphs, ys=ys)


def write_java(data):
    j = gn.jstr
    Lj = ["package fr.eemicraft.droits.ui;", "", "import java.util.HashMap;", "import java.util.List;",
          "import java.util.Map;", "",
          "/** GÉNÉRÉ par pack/gen_droits.py (ne pas modifier à la main) : glyphes et dispositions de /droits. */",
          "public final class Fonds {",
          f"    public static final int AVANCE_FOND = {gn.ADV_BG};",
          "    public static final char[] NEG = {" + ", ".join("'\\u%04x'" % c for c, _ in gn.NEG) + "};",
          "    public static final char[] POS = {" + ", ".join("'\\u%04x'" % c for c, _ in gn.POS) + "};",
          "    /** Avances en quarts de pixel (police lisse, 26/11). */",
          "    public static final int UNITE = 4;",
          "    public static final float[] TAILLES = {" + ", ".join(f"{t}f" for t in gn.TAILLES) + "};",
          f"    public static final int ESPACE = {gn.ESPACE_Q};",
          "    public static final Map<String, Character> GLYPHES = new HashMap<>();",
          "    public static final Map<Character, Integer> AVANCES = new HashMap<>();",
          "    public record Texte(int x, int y, char align, int max) { }",
          "    public record Libelle(String texte, int x, int y, char align, int couleur, String police, int largeur) { }",
          "    public record Disposition(int lignes, Map<String, int[]> boutons, Map<String, int[]> cases,",
          "                              Map<String, Texte> textes, List<Libelle> libelles) { }",
          "    public static final Map<String, Disposition> MENUS = new HashMap<>();", "", "    static {"]
    for k, ch in data["glyphs"].items():
        Lj.append(f"        GLYPHES.put({j(k)}, '{gn.jchar(ch)}');")
    Lj += gn.java_avances()
    for mid, lay in data["layouts"].items():
        Lj.append(f"        {{ // {mid}")
        Lj.append("            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();")
        Lj.append("            Map<String, Texte> t = new HashMap<>();")
        for k, sl in lay["buttons"].items():
            Lj.append(f"            b.put({j(k)}, new int[]{{{', '.join(map(str, sl))}}});")
        for k, sl in lay["cells"].items():
            Lj.append(f"            c.put({j(k)}, new int[]{{{', '.join(map(str, sl))}}});")
        for k, (x, y, al, mx) in lay["texts"].items():
            Lj.append(f"            t.put({j(k)}, new Texte({x}, {y}, '{al}', {mx}));")
        libs = ", ".join(f"new Libelle({j(lb[0])}, {lb[1]}, {lb[2]}, '{lb[3]}', 0x{lb[4][0]:02X}{lb[4][1]:02X}{lb[4][2]:02X}, "
                         f"{j(gn.font_key('dt', lb[2], lb[5]))}, {round(gn.width_k(lb[0], lb[5]) * 4)})"
                         for lb in lay["labels"])
        Lj.append(f"            MENUS.put({j(mid)}, new Disposition({lay['rows']}, b, c, t, List.of({libs})));")
        Lj.append("        }")
    Lj += ["    }", "", "    private Fonds() { }", "}"]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(Lj) + "\n")


EX = {
    "droits": ([], {"haut": "GESTION DES DROITS", "s_joueurs": "12 EN LIGNE", "s_jeux": "2 BÊTA · 1 MAINT.",
                    "s_betas": "4 TESTEURS", "s_interdits": "1 JOUEUR", "bas": "CLIQUE UNE CATÉGORIE"}),
    "droits_fiche": (["droits_fiche:sel:beta", "droits_fiche:ok:droits:0", "droits_fiche:chef:droits:1",
                      "droits_fiche:non:droits:4"],
                     {"haut": "ENOSIK · EN LIGNE · LOBBY", "resume": "2 INTERDITS",
                      "bas": "GAUCHE : ACCORDER · DROIT : REFUSER"}),
    "droits_jeux": (["droits_jeux:chef:liste:2", "droits_jeux:non:liste:5"], {"haut": "2 BÊTA · 1 MAINT.",
                    "page": "1/2"}),
}


def planche(data, v54, path):
    gn.EX.clear()
    gn.EX.update(EX)
    gn.planche(data, v54, path)


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    src, dst = sys.argv[1], sys.argv[2]
    jar = zipfile.ZipFile(gn.JAR)
    v54 = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/container/generic_54.png"))).convert("RGBA")
    pack = zipfile.ZipFile(src)
    if f"assets/{NS}/items/nav/vide.json" not in pack.namelist():
        raise SystemExit("ERREUR : le pack d'entrée n'a pas l'objet invisible eemicraft:nav/vide (gen_nav.py)")
    lob = Image.open(io.BytesIO(pack.read(f"assets/{NS}/textures/font/lobby/bg_profile.png"))).convert("RGBA")
    gn.INV_IMG = lob.crop((0, 17 + 18 * 6, 176, 222))
    ic = gn.load_icons(pack)
    files, data = build_files(v54, ic)
    for n in files:
        if n in pack.namelist() and not reserve(n):
            raise SystemExit(f"ERREUR : {n} existe déjà dans le pack d'entrée")
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for info in pack.infolist():
            if reserve(info.filename):
                continue
            zi = zipfile.ZipInfo(info.filename, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, pack.read(info.filename))
        for n in sorted(files):
            zi = zipfile.ZipInfo(n, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, files[n])
    with open(dst, "wb") as f:
        f.write(out.getvalue())
    write_java(data)
    planche(data, v54, os.path.join(RACINE, "apercus", "menus.png"))
    print(f"{len(data['bases'])} fonds, {len(data['overlays'])} calques, polices de texte : {data['ys']}")
    print(f"Pack écrit : {dst}  SHA-1 {hashlib.sha1(out.getvalue()).hexdigest()}")


if __name__ == "__main__":
    main()
