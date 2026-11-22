"""Génère songs_halloween.yml : playlist Halloween du lobby (même format et mêmes règles que gen_songs.py).

Pour l'activer : copier le fichier sur le serveur en plugins/EemiMusique/songs.yml puis /musique reload
(un songs.yml dans le dossier du plugin remplace celui du jar) ; pour revenir à la playlist normale :
supprimer/renommer ce fichier puis /musique reload.

Œuvres du domaine public (arrangements écrits ici, de mémoire) et une composition originale.
Usage : python gen_halloween.py
"""
import os

PC = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def idx(n):
    pc = PC[n[0]]
    i = 1
    if n[i] == '#':
        pc += 1; i += 1
    elif n[i] == 'b':
        pc -= 1; i += 1
    v = 12 * (int(n[i:]) + 1) + pc - 54
    if not 0 <= v <= 24:
        raise ValueError('note hors plage : ' + n)
    return v


def length(notes):
    total = 0
    for tok in notes.split():
        if tok == '|':
            continue
        name, _, d = tok.partition(':')
        for n in name.split('+'):
            if n != '_':
                idx(n)
        total += int(d) if d else 1
    return total


def arp(chords, pattern):
    return ' | '.join(' '.join(c[i] for i in pattern) for c in chords)


SONGS = []


def song(key, name, tempo, tracks, repeat=1):
    main = length(tracks[0][2])
    for inst, vol, notes in tracks[1:]:
        L = length(notes)
        if main % L:
            raise ValueError('%s : piste %s de longueur %d ne divise pas %d' % (key, inst, L, main))
    SONGS.append((key, name, tempo, repeat, tracks))


# ---------------------------------------------------------------- Antre du roi de la montagne (Grieg)
# Croche = 1 pas, si mineur ramené en mi mineur pour la plage ; thème en pizzicato puis repris par le xylophone.
theme = ('E4 F#4 G4 A4 B4 G4 B4:2 | A#4 F#4 A#4:2 A4 F4 A4:2 | E4 F#4 G4 A4 B4 G4 B4 E5 | D5 B4 G4 B4 D5:4 '
         '| E4 F#4 G4 A4 B4 G4 B4:2 | A#4 F#4 A#4:2 A4 F4 A4:2 | E4 F#4 G4 A4 B4 G4 B4 E5 | D5 B4 G4 B4 E5:4')
song('roi_montagne', 'Antre du roi de la montagne - Edvard Grieg', 3, [
    ('bass', 0.8, theme + ' | ' + theme),
    ('xylophone', 0.5, '_:64 ' + theme),
    ('basedrum', 0.4, 'C4:2 _:2'),
    ('hat', 0.15, '_ C5 _ C5'),
], repeat=2)

# ---------------------------------------------------------------- Marche funèbre (Chopin, sonate n°2)
# Double croche = 1 pas (noire = 4), si bémol mineur.
fun = ('A#3:4 A#3:3 A#3:1 A#3:8 | C#4:3 C4:1 C4:3 A#3:1 A#3:3 A3:1 A#3:4 '
       '| A#3:4 A#3:3 A#3:1 A#3:8 | C#4:3 C4:1 C4:3 A#3:1 A#3:3 A3:1 A#3:4 '
       '| F4:6 D#4:2 C#4:4 C4:4 | A#3:8 _:8 | F4:6 D#4:2 C#4:4 C4:4 | A#3:16')
song('marche_funebre', 'Marche funebre - Frederic Chopin', 4, [
    ('flute', 0.6, fun),
    ('bass', 0.6, fun),
    ('harp', 0.3, 'A#3+C#4+F4:4 F#3+A#3+C#4:4'),
    ('bass', 0.55, 'A#3:8 F#3:8'),
    ('bell', 0.25, 'A#4:16 _:16'),
], repeat=2)

# ---------------------------------------------------------------- Toccata en ré mineur (Bach, BWV 565)
# Double croche = 1 pas, une octave plus bas que l'orgue ; ouverture reprise 3 fois puis la descente.
toc = ('A4 G4 A4:6 _:2 G4 F4 E4 D4 C#4:3 D4:9 _:6 '
       '| A4 G4 A4:6 _:2 G4 F4 E4 D4 C#4:3 D4:9 _:6 '
       '| A4 G4 A4:6 _:2 G4 F4 E4 D4 C#4:3 D4:9 _:6 '
       '| D5 C#5 D5 A4 F4 A4 D4:2 E4 F4 G4 A4 A#4 A4 G4 F4 E4 D4 C#4 D4:8 _:5')
song('toccata', 'Toccata en re mineur - Jean-Sebastien Bach', 3, [
    ('flute', 0.8, toc),
    ('didgeridoo', 0.45, 'D4:8'),
    ('bell', 0.2, 'D5:32'),
], repeat=2)

# ---------------------------------------------------------------- Lobby hanté (original)
# Croche = 1 pas, ré mineur : Dm Bb Gm A, xylophone et boîte à musique.
hant = ('A4:2 D5:2 F5:2 E5:2 | D5:3 C#5 D5:2 A4:2 | A#4:2 G4:2 D5:2 C#5:2 | A4:6 _:2 '
        '| F5:2 E5:2 D5:2 C#5:2 | D5:2 F5:2 A#4:4 | G4:2 A#4:2 D5:2 C#5:2 | D5:6 _:2')
prog = [['D4', 'F4', 'A4'], ['D4', 'F4', 'A#4'], ['D4', 'G4', 'A#4'], ['C#4', 'E4', 'A4']]
song('lobby_hante', 'Lobby hante - composition originale', 4, [
    ('chime', 0.5, hant),
    ('bass', 0.65, ' | '.join(c[0] + ':4 ' + c[0] + ':4' for c in prog)),
    ('harp', 0.35, arp(prog, [0, 1, 2, 1, 0, 1, 2, 1])),
    ('basedrum', 0.35, 'C4:4 _:4'),
    ('bit', 0.12, '_:6 D5 _'),
], repeat=3)


# ---------------------------------------------------------------- Frère Jacques en mineur (Mahler, symphonie n°1, 3e mvt)
# Croche = 1 pas, ré mineur, timbales ré-la en boucle ; la mélodie revient en canon (harpe puis boîte à musique).
mah = ('D4:2 E4:2 F4:2 D4:2 | D4:2 E4:2 F4:2 D4:2 | F4:2 G4:2 A4:4 | F4:2 G4:2 A4:4 '
       '| A4 A#4 A4 G4 F4:2 D4:2 | A4 A#4 A4 G4 F4:2 D4:2 | D4:2 A3:2 D4:4 | D4:2 A3:2 D4:4')
song('mahler', 'Frere Jacques en mineur - Gustav Mahler', 5, [
    ('bass', 0.75, ' '.join([mah, mah, mah])),
    ('basedrum', 0.5, 'D4:2 A3:2'),
    ('harp', 0.5, '_:64 ' + mah + ' ' + mah),
    ('bell', 0.35, '_:128 ' + mah),
], repeat=1)

# ---------------------------------------------------------------- Berceuse hantée (original)
# Boîte à musique en la mineur, 3/4, triton (ré dièse) qui grince, battement de cœur.
ber = ('E5:2 C5:2 A4:2 | D#5:4 C5:2 | B4:2 G#4:2 E4:2 | A4:6 '
       '| E5:2 C5:2 A4:2 | F5:4 E5:2 | D#5:2 B4:2 G#4:2 | A4:6')
song('berceuse', 'Berceuse hantee - composition originale', 7, [
    ('bell', 0.6, ber),
    ('bass', 0.55, 'A3:6 A3:6 E4:6 A3:6 A3:6 F4:6 E4:6 A3:6'),
    ('didgeridoo', 0.3, 'A3:6'),
    ('basedrum', 0.45, 'C4 _ C4 _:9'),
], repeat=3)

# ---------------------------------------------------------------- Brouillard (original)
# Ambiance : notes isolées au carillon, bourdon grave qui glisse d'un demi-ton, cœur qui bat.
bro = ('D5:4 _:4 A4:2 _:6 | G#4:8 _:8 | F5:4 _:4 E5:2 _:2 C#5:4 | D5:12 _:4 '
       '| A#4:4 _:4 A4:2 _:6 | D#5:8 _:8 | D5:4 C#5:4 D5:8 | _:16')
song('brouillard', 'Brouillard - composition originale', 6, [
    ('chime', 0.55, bro),
    ('didgeridoo', 0.5, ' '.join(['D4:8'] * 8 + ['C#4:8'] * 4 + ['D4:8'] * 4)),
    ('basedrum', 0.35, 'C4 _ C4 _:13'),
    ('xylophone', 0.15, '_:56 G#4 _:7 _:56 D#5 _:7'),
], repeat=2)

out = ['# Playlist Halloween, generee par gen_halloween.py.', 'songs:']
for key, name, tempo, repeat, tracks in SONGS:
    out += ['  %s:' % key, '    name: "%s"' % name, '    tempo: %d' % tempo, '    repeat: %d' % repeat, '    tracks:']
    for inst, vol, notes in tracks:
        out += ['      - instrument: %s' % inst, '        volume: %s' % vol, '        notes: "%s"' % ' '.join(notes.split())]
    print('%-16s %5.0f s' % (key, length(tracks[0][2]) * tempo * repeat / 20))
# + quelques morceaux sombres de la playlist normale (copiés tels quels depuis le songs.yml du jar)
KEEP = []
src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src', 'main', 'resources', 'songs.yml'), encoding='utf-8').read().split(chr(10))
block = None
for line in src:
    if line.startswith('  ') and not line.startswith('    ') and line.strip().endswith(':'):
        block = line.strip()[:-1]
    if block in KEEP and line.startswith('  '):
        out.append(line)

path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'songs_halloween.yml')
open(path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
