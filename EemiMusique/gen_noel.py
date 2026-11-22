"""Genere songs_noel.yml : playlist de Noel du lobby (meme format et memes regles que gen_halloween.py).

Sortie : ../EemiNoel/src/main/resources/songs_noel.yml (le plugin EemiNoel la reconnait a sa premiere ligne).
Format lu par EemiMusique : une piste = instrument (block.note_block.<instrument>), volume, notes ;
« C4:2 D4+F#4 _:3 | ... », un pas = tempo ticks, notes jouees au debut de leur duree (un son de bloc musical
ne tient pas : les basses et accords sont rejoues, jamais tenus). Plage F#3..F#5. La premiere piste fixe la
longueur du morceau, les autres bouclent et doivent diviser cette longueur.

Oeuvres du domaine public (arrangements ecrits ici, de memoire) et deux compositions originales.
Usage : python gen_noel.py
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


def bars_len(notes, bar):
    """Verifie que chaque mesure (separee par |) fait bien `bar` pas."""
    for i, m in enumerate(notes.split('|')):
        if m.strip() and length(m) != bar:
            raise ValueError('mesure %d de longueur %d au lieu de %d : %s' % (i + 1, length(m), bar, m.strip()))
    return notes


SONGS = []


def song(key, name, tempo, tracks, repeat=1):
    main = length(tracks[0][2])
    for inst, vol, notes in tracks[1:]:
        L = length(notes)
        if main % L:
            raise ValueError('%s : piste %s de longueur %d ne divise pas %d' % (key, inst, L, main))
    SONGS.append((key, name, tempo, repeat, tracks))


# ---------------------------------------------------------------- accords
# Voicings dans la plage (sous la melodie), basse : (fondamentale, quinte).
VOICE = {
    'C': ['C4', 'E4', 'G4'], 'C7': ['G3', 'A#3', 'C4', 'E4'], 'F': ['A3', 'C4', 'F4'], 'G': ['G3', 'B3', 'D4'],
    'G7': ['G3', 'B3', 'D4', 'F4'], 'D': ['F#3', 'A3', 'D4'], 'D7': ['F#3', 'A3', 'C4', 'D4'],
    'A': ['A3', 'C#4', 'E4'], 'A7': ['G3', 'A3', 'C#4', 'E4'], 'Am': ['A3', 'C4', 'E4'], 'Em': ['G3', 'B3', 'E4'],
    'Bm': ['F#3', 'B3', 'D4'], 'B7': ['F#3', 'A3', 'B3', 'D#4'], 'Bb': ['A#3', 'D4', 'F4'], 'Dm': ['A3', 'D4', 'F4'],
}
BASS = {
    'C': ('C4', 'G3'), 'C7': ('C4', 'G3'), 'F': ('F4', 'C4'), 'G': ('G3', 'D4'), 'G7': ('G3', 'D4'),
    'D': ('D4', 'A3'), 'D7': ('D4', 'A3'), 'A': ('A3', 'E4'), 'A7': ('A3', 'E4'), 'Am': ('A3', 'E4'),
    'Em': ('E4', 'B3'), 'Bm': ('B3', 'F#3'), 'B7': ('B3', 'F#3'), 'Bb': ('A#3', 'F4'), 'Dm': ('D4', 'A3'),
}


def ch(c):
    return '+'.join(VOICE[c])


def comp(chords, bar, n, fn):
    """Accompagnement : une entree par mesure (accord ou liste d'accords qui se partagent la mesure),
    chaque mesure decoupee en n temps ; fn(accord, longueur du temps, numero du temps) -> notes."""
    out = []
    for e in chords:
        segs = [e] * n if isinstance(e, str) else e
        if len(segs) != n:
            segs = [c for c in segs for _ in range(n // len(segs))]
        if len(segs) != n or bar % n:
            raise ValueError('mesure mal decoupee : %r' % (e,))
        L = bar // n
        out.append(' '.join(fn(c, L, i) for i, c in enumerate(segs)))
    return ' | '.join(out)


# ---------------------------------------------------------------- Douce nuit (Franz Xaver Gruber, 1818)
# 6/8, double croche = 1 pas (mesure 12), do majeur. Boite a musique, harpe en croches, basse a la noire pointee.
nuit = bars_len('G4:3 A4:1 G4:2 E4:6 | G4:3 A4:1 G4:2 E4:6 | D5:4 D5:2 B4:6 | C5:4 C5:2 G4:6 '
                '| A4:4 A4:2 C5:3 B4:1 A4:2 | G4:3 A4:1 G4:2 E4:6 | A4:4 A4:2 C5:3 B4:1 A4:2 | G4:3 A4:1 G4:2 E4:6 '
                '| D5:4 D5:2 F5:3 D5:1 B4:2 | C5:6 E5:6 | C5:3 G4:1 E4:2 G4:3 F4:1 D4:2 | C4:12', 12)
nuit_ch = ['C', 'C', 'G', 'C', 'F', 'C', 'F', 'C', 'G7', 'C', ['C', 'G7'], 'C']
song('douce_nuit', 'Douce nuit - Franz Xaver Gruber', 4, [
    ('bell', 0.55, nuit),
    ('flute', 0.25, nuit),
    ('harp', 0.35, comp(nuit_ch, 12, 2, lambda c, L, i: ' '.join(VOICE[c][k] + ':2' for k in (0, 1, 2)))),
    ('bass', 0.5, comp(nuit_ch, 12, 2, lambda c, L, i: BASS[c][i % 2] + ':6')),
    ('chime', 0.12, 'C5:24 _:24'),
], repeat=2)

# ---------------------------------------------------------------- Jingle Bells (James Lord Pierpont, 1857)
# 4/4, double croche = 1 pas (mesure 16), sol majeur : couplet puis deux refrains. Pompe basse / guitare, grelots.
couplet = ('D4:2 B4:2 A4:2 G4:2 D4:6 D4:1 D4:1 | D4:2 B4:2 A4:2 G4:2 E4:8 | E4:2 C5:2 B4:2 A4:2 F#4:8 '
           '| D5:2 D5:2 C5:2 A4:2 B4:8 | D4:2 B4:2 A4:2 G4:2 D4:8 | D4:2 B4:2 A4:2 G4:2 E4:6 E4:2 '
           '| E4:2 C5:2 B4:2 A4:2 D5:2 D5:2 D5:2 D5:2 | E5:2 D5:2 C5:2 A4:2 G4:8')
refr = ('B4:2 B4:2 B4:4 B4:2 B4:2 B4:4 | B4:2 D5:2 G4:3 A4:1 B4:8 | C5:2 C5:2 C5:3 C5:1 C5:2 B4:2 B4:2 B4:1 B4:1 '
        '| B4:2 A4:2 A4:2 B4:2 A4:4 D5:4 | B4:2 B4:2 B4:4 B4:2 B4:2 B4:4 | B4:2 D5:2 G4:3 A4:1 B4:8 '
        '| C5:2 C5:2 C5:3 C5:1 C5:2 B4:2 B4:2 B4:1 B4:1 | D5:2 D5:2 C5:2 A4:2 G4:8')
jingle = bars_len(couplet + ' | ' + refr + ' | ' + refr, 16)
c_ch = ['G', ['G', 'C'], ['Am', 'D'], ['D7', 'G'], 'G', ['G', 'C'], ['Am', 'D'], ['D7', 'G']]
r_ch = ['G', 'G', ['C', 'G'], ['A7', 'D7'], 'G', 'G', ['C', 'G'], ['D7', 'G']]
j_ch = c_ch + r_ch + r_ch
song('jingle_bells', 'Jingle Bells - James Lord Pierpont', 2, [
    ('bell', 0.55, jingle),
    ('flute', 0.3, jingle),
    ('bass', 0.6, comp(j_ch, 16, 2, lambda c, L, i: BASS[c][i % 2] + ':4 _:4')),
    ('guitar', 0.35, comp(j_ch, 16, 2, lambda c, L, i: '_:4 ' + ch(c) + ':4')),
    ('basedrum', 0.35, 'C4:8'),
    ('snare', 0.2, '_:4 C4:4'),
    ('hat', 0.12, 'C5:2'),
], repeat=2)

# ---------------------------------------------------------------- Mon beau sapin (trad. allemand, O Tannenbaum)
# 3/4, double croche = 1 pas (mesure 12), sol majeur. Mesure 0 = levee. Valse basse / harpe.
sapin = bars_len('_:8 D4:4 | G4:3 G4:1 G4:6 A4:2 | B4:3 B4:1 B4:6 B4:2 | A4:2 B4:2 C5:4 F#4:4 '
                 '| A4:4 G4:4 D5:2 D5:2 | B4:4 E5:3 D5:1 D5:2 C5:2 | C5:6 C5:2 C5:2 A4:2 '
                 '| D5:3 C5:1 C5:2 B4:2 B4:2 D4:2 | G4:3 G4:1 G4:6 A4:2 | B4:3 B4:1 B4:6 B4:2 '
                 '| A4:2 B4:2 C5:4 F#4:4 | A4:4 G4:8', 12)
s_ch = ['G', 'G', 'G', 'D7', ['D7', 'G', 'G'], ['G', 'C', 'C'], 'Am', ['D7', 'G', 'G'], 'G', 'G', 'D7', ['D7', 'G', 'G']]
song('mon_beau_sapin', 'Mon beau sapin - trad.', 3, [
    ('flute', 0.55, sapin),
    ('bell', 0.3, sapin),
    ('bass', 0.55, comp(s_ch, 12, 3, lambda c, L, i: (BASS[c][0] + ':4') if i == 0 else '_:4')),
    ('harp', 0.35, comp(s_ch, 12, 3, lambda c, L, i: '_:4' if i == 0 else ch(c) + ':4')),
    ('chime', 0.12, 'D5:48'),
], repeat=3)

# ---------------------------------------------------------------- We Wish You a Merry Christmas (trad. anglais)
# 3/4, croche = 1 pas (mesure 6), sol majeur : couplet puis « Good tidings », la levee est en fin de morceau.
wish = bars_len('G4:2 G4:1 A4:1 G4:1 F#4:1 | E4:2 E4:2 E4:2 | A4:2 A4:1 B4:1 A4:1 G4:1 | F#4:2 D4:2 D4:2 '
                '| B4:2 B4:1 C5:1 B4:1 A4:1 | G4:2 E4:2 D4:1 D4:1 | E4:2 A4:2 F#4:2 | G4:4 D4:2 '
                '| G4:2 G4:2 G4:2 | F#4:4 F#4:2 | G4:2 F#4:2 E4:2 | D4:4 A4:2 '
                '| B4:2 A4:2 G4:2 | D5:2 D4:2 D4:1 D4:1 | E4:2 A4:2 F#4:2 | G4:4 D4:2', 6)
w_ch = ['G', 'C', 'A7', 'D', 'B7', 'Em', ['C', 'D', 'D'], 'G', 'G', 'D', 'A7', 'D', 'G', 'D', ['C', 'D', 'D'], 'G']
song('we_wish', 'We Wish You a Merry Christmas - trad.', 4, [
    ('bell', 0.55, wish),
    ('flute', 0.3, wish),
    ('bass', 0.55, comp(w_ch, 6, 3, lambda c, L, i: (BASS[c][0] + ':2') if i == 0 else '_:2')),
    ('guitar', 0.3, comp(w_ch, 6, 3, lambda c, L, i: '_:2' if i == 0 else ch(c) + ':2')),
    ('basedrum', 0.3, 'C4:6'),
    ('hat', 0.1, '_:2 C5:2 C5:2'),
], repeat=3)

# ---------------------------------------------------------------- Deck the Halls (trad. gallois)
# 4/4, croche = 1 pas (mesure 8), fa majeur. Harpe en arpeges, caisse claire sur 2 et 4.
deck = bars_len('C5:3 A#4:1 A4:2 G4:2 | F4:2 G4:2 A4:2 F4:2 | G4:1 A4:1 A#4:1 G4:1 A4:3 G4:1 | F4:2 E4:2 F4:4 '
                '| C5:3 A#4:1 A4:2 G4:2 | F4:2 G4:2 A4:2 F4:2 | G4:1 A4:1 A#4:1 G4:1 A4:3 G4:1 | F4:2 E4:2 F4:4 '
                '| G4:3 A4:1 A#4:2 G4:2 | A4:3 A#4:1 C5:2 G4:2 | A4:1 B4:1 C5:2 D5:1 E5:1 F5:2 | E5:2 D5:2 C5:4 '
                '| C5:3 A#4:1 A4:2 G4:2 | F4:2 G4:2 A4:2 F4:2 | D5:1 D5:1 D5:1 D5:1 C5:3 A#4:1 | A4:2 G4:2 F4:4', 8)
d_ch = [['F', 'C7'], 'F', ['C7', 'F'], ['C7', 'F'], ['F', 'C7'], 'F', ['C7', 'F'], ['C7', 'F'],
        'C7', ['F', 'C'], ['F', 'Dm'], ['G7', 'C'], ['F', 'C7'], 'F', ['Bb', 'F'], ['C7', 'F']]
song('deck_the_halls', 'Deck the Halls - trad.', 4, [
    ('flute', 0.55, deck),
    ('bell', 0.3, deck),
    ('harp', 0.35, comp(d_ch, 8, 2, lambda c, L, i: ' '.join(VOICE[c][k] + ':1' for k in (0, 1, 2, 1)))),
    ('bass', 0.55, comp(d_ch, 8, 2, lambda c, L, i: BASS[c][i % 2] + ':2 _:2')),
    ('basedrum', 0.3, 'C4:4'),
    ('snare', 0.18, '_:2 C4:2'),
], repeat=2)

# ---------------------------------------------------------------- Joy to the World (Haendel, arr. Lowell Mason, 1839)
# 2/4, double croche = 1 pas (mesure 8), re majeur. Cloches en tete, basse a la noire, guitare a contretemps.
joy = bars_len('D5:4 C#5:3 B4:1 | A4:6 G4:2 | F#4:4 E4:4 | D4:6 A4:2 | B4:6 B4:2 | C#5:6 C#5:2 | D5:6 D5:2 '
               '| D5:2 C#5:2 B4:2 A4:2 | A4:3 G4:1 F#4:2 D5:2 | D5:2 C#5:2 B4:2 A4:2 | A4:3 G4:1 F#4:2 F#4:2 '
               '| F#4:2 F#4:2 F#4:2 F#4:1 G4:1 | A4:6 G4:1 F#4:1 | E4:2 E4:2 E4:2 E4:1 F#4:1 | G4:6 F#4:1 E4:1 '
               '| D4:2 D5:4 B4:2 | A4:3 G4:1 F#4:2 G4:2 | F#4:4 E4:4 | D4:8 | _:8', 8)
jo_ch = ['D', ['D', 'G'], ['D', 'A'], 'D', 'G', 'A', 'D', 'D', 'D', 'D', 'D', 'D', 'D', 'A', 'A7',
         ['D', 'G'], ['D', 'A7'], ['D', 'A'], 'D', 'D']
song('joy_to_the_world', 'Joy to the World - Haendel et Lowell Mason', 3, [
    ('bell', 0.55, joy),
    ('flute', 0.3, joy),
    ('bass', 0.55, comp(jo_ch, 8, 2, lambda c, L, i: BASS[c][i % 2] + ':4')),
    ('guitar', 0.3, comp(jo_ch, 8, 2, lambda c, L, i: '_:2 ' + ch(c) + ':2')),
    ('basedrum', 0.25, 'C4:8'),
    ('chime', 0.12, '_:152 D5:8'),
], repeat=3)

# ---------------------------------------------------------------- Noel au lobby (original)
# 4/4, croche = 1 pas (mesure 8), sol majeur, joyeux : clochettes, basse bondissante, batterie legere.
lobby = bars_len('B4:1 D5:1 G4:1 D5:1 B4:2 A4:1 G4:1 | E4:2 G4:1 B4:1 E5:4 | E5:1 D5:1 C5:1 E5:1 G4:2 C5:2 '
                 '| D5:2 A4:1 F#4:1 A4:4 | B4:1 D5:1 G4:1 D5:1 B4:2 A4:1 G4:1 | E5:2 F#5:1 E5:1 D5:2 B4:2 '
                 '| C5:2 E5:2 D5:2 F#4:2 | G4:4 _:2 D5:2 | E5:1 D5:1 C5:2 E5:2 G4:2 | F#5:1 E5:1 D5:2 A4:2 F#4:2 '
                 '| B4:1 D5:1 F#5:2 D5:2 B4:2 | E5:2 D5:1 B4:1 G4:4 | C5:1 E5:1 G4:1 C5:1 E5:2 D5:2 '
                 '| D5:1 C5:1 A4:1 F#4:1 D4:2 F#4:2 | A4:2 C5:2 F#5:2 E5:1 F#5:1 | G4:2 D5:1 B4:1 G4:4', 8)
l_ch = ['G', 'Em', 'C', 'D', 'G', 'Em', ['C', 'D'], 'G', 'C', 'D', 'Bm', 'Em', 'C', 'D', ['Am', 'D'], 'G']
song('noel_lobby', 'Noel au lobby - composition originale', 4, [
    ('chime', 0.45, lobby),
    ('bell', 0.4, lobby),
    ('bass', 0.6, comp(l_ch, 8, 2, lambda c, L, i: '%s:2 %s:1 %s:1' % (BASS[c][0], BASS[c][1], BASS[c][0]))),
    ('harp', 0.3, comp(l_ch, 8, 2, lambda c, L, i: '_:1 %s:1 _:1 %s:1' % (ch(c), ch(c)))),
    ('basedrum', 0.35, 'C4:4'),
    ('snare', 0.18, '_:2 C4:2'),
    ('hat', 0.1, '_:1 C5:1'),
], repeat=3)

# ---------------------------------------------------------------- Flocons (original)
# 3/4, croche = 1 pas (mesure 6), re majeur, calme : boite a musique, harpe en croches, quelques eclats de carillon.
flo = bars_len('F#5:2 E5:1 D5:1 A4:2 | B4:2 A4:1 B4:1 D5:2 | A4:4 F#4:2 | E4:2 A4:2 C#5:2 '
               '| D5:2 F#5:2 E5:1 D5:1 | D5:2 B4:2 F#4:2 | E5:3 C#5:1 A4:2 | D5:6 '
               '| B4:1 C#5:1 D5:2 B4:2 | A4:2 F#4:2 D4:2 | E4:1 F#4:1 G4:2 B4:2 | A4:4 C#5:2 '
               '| F#5:2 E5:1 D5:1 A4:2 | B4:2 D5:2 G4:2 | A4:2 E5:2 C#5:2 | D5:6', 6)
f_ch = ['D', 'G', 'D', 'A', 'D', 'Bm', 'A', 'D', 'G', 'D', 'Em', 'A', 'D', 'G', 'A', 'D']
song('flocons', 'Flocons - composition originale', 4, [
    ('bell', 0.55, flo),
    ('harp', 0.35, comp(f_ch, 6, 3, lambda c, L, i: '%s:1 %s:1' % (VOICE[c][(2 * i) % 3], VOICE[c][(2 * i + 1) % 3]))),
    ('bass', 0.5, comp(f_ch, 6, 3, lambda c, L, i: (BASS[c][0] + ':2') if i == 0 else '_:2')),
    ('chime', 0.15, 'D5:1 _:11 A4:1 _:11'),
], repeat=3)

out = ['# Playlist Noel, generee par gen_noel.py.', 'songs:']
total = 0
for key, name, tempo, repeat, tracks in SONGS:
    out += ['  %s:' % key, '    name: "%s"' % name, '    tempo: %d' % tempo, '    repeat: %d' % repeat, '    tracks:']
    for inst, vol, notes in tracks:
        out += ['      - instrument: %s' % inst, '        volume: %s' % vol, '        notes: "%s"' % ' '.join(notes.split())]
    d = length(tracks[0][2]) * tempo * repeat / 20
    total += d
    print('%-18s %5.0f s' % (key, d))
print('%-18s %5.0f s (%d morceaux)' % ('total', total, len(SONGS)))

path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'EemiNoel', 'src', 'main', 'resources', 'songs_noel.yml')
os.makedirs(os.path.dirname(path), exist_ok=True)
open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
print('ecrit :', path)
