"""Génère src/main/resources/songs.yml (musique d'ambiance du lobby, sons de blocs musicaux).

Notation d'une piste : jetons séparés par des espaces, « | » ignoré (barres de mesure).
  NOTE[:durée]   ex. F#4:2   (durée en pas, 1 par défaut)
  A+B+C[:durée]  accord (plusieurs notes au même pas)
  _[:durée]      silence
Les notes doivent tenir dans la plage d'un bloc musical : F#3 (0) .. F#5 (24). Pour les instruments
graves (bass, didgeridoo) le jeu les fait sonner deux octaves plus bas ; pour les percussions
(basedrum, snare, hat) la note ne règle que la hauteur du bruit.
La 1re piste fixe la longueur du morceau ; les autres bouclent sur leur propre longueur.

Œuvres : domaine public (compositeurs morts depuis plus de 70 ans, airs traditionnels), arrangements
écrits ici ; « Lobby EEMICRAFT » et « Nuit au lobby » sont des compositions originales.
Usage : python gen_songs.py
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


def rep(s, n):
    return ' '.join([s] * n)


def arp(chords, pattern, dur=1):
    """Arpège : pour chaque accord (liste de notes), joue les notes dans l'ordre de pattern (indices)."""
    return ' | '.join(' '.join(c[i] + (':%d' % dur if dur > 1 else '') for i in pattern) for c in chords)


SONGS = []


def song(key, name, tempo, tracks, repeat=1):
    main = length(tracks[0][2])
    for inst, vol, notes in tracks[1:]:
        L = length(notes)
        if main % L:
            raise ValueError('%s : piste %s de longueur %d ne divise pas %d' % (key, inst, L, main))
    SONGS.append((key, name, tempo, repeat, tracks))


# ---------------------------------------------------------------- Canon en ré (Pachelbel)
# Croche = 1 pas. Basse obstinée (2 temps par accord), 3 voix en canon décalées de 2 mesures.
A = 'F#5:4 E5:4 D5:4 C#5:4 B4:4 A4:4 B4:4 C#5:4'
B = 'D5:2 C#5:2 B4:2 A4:2 G4:2 F#4:2 G4:2 E4:2 D4:2 F#4:2 A4:2 G4:2 F#4:2 D4:2 F#4:2 E4:2'
C = 'D4 F#4 A4 G4 F#4 D4 F#4 E4 D4 B3 D4 A4 G4 B4 A4 G4 F#4 D4 E4 C#5 D5 F#5 D5 A4 B4 G4 B4 A4 G4 F#4 G4 E4'
canon = ' '.join([A, B, C, A, B])
chords_canon = [['D4', 'F#4', 'A4'], ['C#4', 'E4', 'A4'], ['D4', 'F#4', 'B4'], ['C#4', 'F#4', 'A4'],
                ['D4', 'G4', 'B4'], ['D4', 'F#4', 'A4'], ['D4', 'G4', 'B4'], ['C#4', 'E4', 'A4']]
song('canon', 'Canon en re - Johann Pachelbel', 5, [
    ('flute', 0.7, canon + ' D5:32'),
    ('bass', 0.8, 'D4:4 A3:4 B3:4 F#3:4 G3:4 D4:4 G3:4 A3:4'),
    ('harp', 0.45, arp(chords_canon, [0, 1, 2, 1])),
    ('bell', 0.35, '_:32 ' + ' '.join([A, B, C, A]) + ' F#4:32'),
])

# ---------------------------------------------------------------- Gymnopedie n°1 (Satie)
# Noire = 1 pas, 3/4, transposée d'une tierce mineure vers le bas (mi majeur 7 / si majeur 7).
mel = ('_:12 | _ D#5 F#5 | E5 D#5 A#4 | G#4 A#4 B4 | F#4:3 | D#4:3 | D#4:3 | D#4:3 | _:3 '
       '| _ D#5 F#5 | E5 D#5 A#4 | G#4 A#4 B4 | F#4:3 | A#4:3 | D#5:3 | C#5:3 | C#5:3 '
       '| _ G#4 B4 | A#4 G#4 F#4 | D#4 F#4 G#4 | C#4:3 | D#4:3 | E4:3 | F#4:3 | B3:3 '
       '| _ D#5 F#5 | E5 D#5 A#4 | G#4 A#4 B4 | F#4:3 | D#4:3 | D#4:3 | D#4:6')
song('gymnopedie', 'Gymnopedie n.1 - Erik Satie', 13, [
    ('harp', 0.7, mel),
    ('bass', 0.7, 'E4:3 B3:3'),
    ('harp', 0.3, '_ G#4+B4+D#5:2 _ D#4+F#4+A#4:2'),
    ('chime', 0.15, '_:5 B4 _:6'),
])

# ---------------------------------------------------------------- Prelude en do (Bach, BWV 846)
# Double croche = 1 pas, transposé en sol (une quarte plus bas) pour tenir dans la plage.
bars = [['G3', 'B3', 'D4', 'G4', 'B4'], ['G3', 'A3', 'E4', 'A4', 'C5'], ['F#3', 'A3', 'D4', 'A4', 'C5'],
        ['G3', 'B3', 'D4', 'G4', 'B4'], ['G3', 'B3', 'E4', 'B4', 'E5'], ['G3', 'A3', 'C#4', 'E4', 'A4'],
        ['F#3', 'A3', 'D4', 'A4', 'D5'], ['F#3', 'G3', 'B3', 'D4', 'G4'], ['A3', 'D4', 'F#4', 'C5', 'D5'],
        ['A3', 'D4', 'F#4', 'A4', 'D5']]
prelude = ' | '.join(' '.join(b[i] for i in [0, 1, 2, 3, 4, 2, 3, 4] * 2) for b in bars)
song('prelude', 'Prelude en do - Jean-Sebastien Bach', 3, [
    ('harp', 0.7, prelude),
    ('bass', 0.6, ' | '.join(b[0] + ':16' for b in bars)),
    ('bell', 0.2, ' | '.join('_:8 ' + b[4] + ':8' for b in bars)),
], repeat=2)

# ---------------------------------------------------------------- Lettre a Elise (Beethoven)
# Double croche = 1 pas.
elise_a = 'E5 D#5 E5 D#5 E5 B4 D5 C5 A4:2 _ C4 E4 A4 B4:2 _ E4 G#4 B4 C5:2 _ E4 E5 D#5 E5 D#5 E5 B4 D5 C5 A4:2 _ C4 E4 A4 B4:2 _ E4 C5 B4 A4:3 _'
elise_b = 'B4 C5 D5 E5:3 G4 F5 E5 D5:3 F4 E5 D5 C5:3 E4 D5 C5 B4:3 _ E4 E5 _:2 E5 E5 _:2 D#5 E5 _:2 D#5 E5 D#5'
elise = ' '.join([elise_a, elise_b, elise_a])
bass_a = '_:8 A4:6 E4:6 A4:4 _:8 A4:6 E4:6 A4:4'
bass_b = 'C4:6 G3:6 A3:6 E4:6 _:16'
song('elise', 'Lettre a Elise - Ludwig van Beethoven', 3, [
    ('harp', 0.75, elise),
    ('bass', 0.6, ' '.join([bass_a, bass_b, bass_a])),
], repeat=2)

# ---------------------------------------------------------------- Greensleeves (traditionnel)
# Croche = 1 pas, 3/4, transposé en sol mineur.
gs_verse = ('A#4:4 C5:2 | D5:3 D#5:1 D5:2 | C5:4 A4:2 | F4:3 G4:1 A4:2 | A#4:4 G4:2 | G4:3 F#4:1 G4:2 '
            '| A4:4 F#4:2 | D4:4 G4:2 | A#4:4 C5:2 | D5:3 D#5:1 D5:2 | C5:4 A4:2 | F4:3 G4:1 A4:2 '
            '| A#4:3 A4:1 G4:2 | F#4:3 E4:1 F#4:2 | G4:6 | G4:4 G4:2')
gs_chorus = ('F5:6 | F5:3 E5:1 D5:2 | C5:4 A4:2 | F4:3 G4:1 A4:2 | A#4:4 G4:2 | G4:3 F#4:1 G4:2 '
             '| A4:4 F#4:2 | D4:6 | F5:6 | F5:3 E5:1 D5:2 | C5:4 A4:2 | F4:3 G4:1 A4:2 '
             '| A#4:3 A4:1 G4:2 | F#4:3 E4:1 F#4:2 | G4:6 | G4:6')
roots_v = ['G4', 'A#4', 'F4', 'F4', 'G4', 'G4', 'D4', 'D4', 'G4', 'A#4', 'F4', 'F4', 'G4', 'D4', 'G4', 'G4']
roots_c = ['A#4', 'A#4', 'F4', 'F4', 'G4', 'G4', 'D4', 'D4', 'A#4', 'A#4', 'F4', 'F4', 'G4', 'D4', 'G4', 'G4']
pads = {'G4': 'G4+A#4+D5', 'A#4': 'F4+A#4+D5', 'F4': 'F4+A4+C5', 'D4': 'F#4+A4+D5'}
roots = roots_v + roots_c
song('greensleeves', 'Greensleeves - air traditionnel anglais', 5, [
    ('flute', 0.7, gs_verse + ' | ' + gs_chorus),
    ('bass', 0.6, ' | '.join(r + ':6' for r in roots)),
    ('guitar', 0.3, ' | '.join('_:2 ' + pads[r] + ':2 ' + pads[r] + ':2' for r in roots)),
], repeat=2)

# ---------------------------------------------------------------- Au matin (Grieg, Peer Gynt)
# Croche = 1 pas, 6/8, mi majeur une octave plus bas.
mm = ('G#4:2 E4 D#4 C#4 D#4 | E4:2 G#4 E4 D#4 C#4 | D#4 E4 D#4 E4 G#4 E4 | G#4 A4 E4 A4:3 '
      '| G#4:2 E4 D#4 C#4 D#4 | E4:2 G#4 E4 D#4 C#4 | D#4 E4 D#4 E4 G#4 E4 | G#4 A4 E4 A4 B4 C#5 '
      '| B4:2 G#4 F#4 E4 F#4 | G#4:2 B4 G#4 F#4 E4 | F#4 G#4 F#4 G#4 B4 G#4 | B4 C#5 G#4 C#5:3 '
      '| B4:2 G#4 F#4 E4 F#4 | G#4:2 E4 C#4 B3 C#4 | E4:6 | E4:6')
roots_mm = ['E4', 'E4', 'E4', 'A3', 'E4', 'E4', 'E4', 'A3', 'B3', 'B3', 'B3', 'C#4', 'B3', 'A3', 'E4', 'E4']
triads = {'E4': ['E4', 'G#4', 'B4'], 'A3': ['E4', 'A4', 'C#5'], 'B3': ['D#4', 'F#4', 'B4'], 'C#4': ['E4', 'G#4', 'C#5']}
song('matin', 'Au matin (Peer Gynt) - Edvard Grieg', 4, [
    ('flute', 0.7, mm),
    ('bass', 0.6, ' | '.join(r + ':6' for r in roots_mm)),
    ('harp', 0.35, arp([triads[r] for r in roots_mm], [0, 1, 2, 1, 2, 1])),
    ('chime', 0.12, '_:12 E5:12'),
], repeat=2)

# ---------------------------------------------------------------- Lobby EEMICRAFT (original)
# Croche = 1 pas, ré majeur, D A Bm G. Pop entraînante avec batterie.
hook = ('F#5:2 E5 D5 A4:2 D5:2 | E5:3 C#5 A4:4 | D5:2 F#5:2 E5 D5 B4:2 | D5:3 B4 A4:2 F#4:2 '
        '| F#5:2 E5 D5 A4:2 D5:2 | E5:2 F#5:2 E5 C#5 A4:2 | B4:2 D5:2 F#5:2 E5 D5 | D5:6 _:2')
bridge = ('B4:2 D5:2 F#5:4 | G4:2 B4:2 D5:4 | A4:2 D5:2 F#5:4 | A4:2 C#5:2 E5:4 '
          '| B4:2 D5:2 F#5:4 | G4:2 B4:2 D5:4 | F#4:2 A4:2 D5:2 F#5:2 | E5:6 _:2')
prog = [['D4', 'F#4', 'A4'], ['C#4', 'E4', 'A4'], ['D4', 'F#4', 'B4'], ['D4', 'G4', 'B4']]
prog_b = [['D4', 'F#4', 'B4'], ['D4', 'G4', 'B4'], ['D4', 'F#4', 'A4'], ['C#4', 'E4', 'A4']]
song('lobby', 'Lobby EEMICRAFT - composition originale', 4, [
    ('bell', 0.55, ' | '.join([hook, bridge, hook])),
    ('bass', 0.75, ' | '.join(' '.join([c[0]] * 8) for c in prog * 2 + prog_b * 2 + prog * 2)),
    ('harp', 0.35, arp(prog * 2 + prog_b * 2 + prog * 2, [0, 1, 2, 1, 0, 1, 2, 1])),
    ('basedrum', 0.5, 'C4:4 C4:4'),
    ('snare', 0.3, '_:2 F#4:2 _:2 F#4:2'),
    ('hat', 0.18, 'C5 C5 C5 C5 C5 C5 C5 C5'),
], repeat=2)

# ---------------------------------------------------------------- Nuit au lobby (original)
# Croche = 1 pas, la mineur, Am7 Dm7 G Cmaj7, ambiance lo-fi calme.
night = ('E5:4 C5:2 A4:2 | F5:4 E5:2 D5:2 | D5:3 B4 G4:4 | E5:6 _:2 '
         '| A4:2 C5:2 E5:4 | D5:2 F5:2 A4:4 | B4:2 D5:2 G4:2 B4:2 | C5:8 '
         '| _:8 | _:4 A4:2 C5:2 | D5:3 B4 G4:4 | E5:8 '
         '| A4:2 C5:2 E5:2 C5:2 | D5:2 A4:2 F4:2 A4:2 | B4:2 G4:2 D5:2 B4:2 | C5:8')
lofi = [['A3', 'C4', 'E4', 'G4'], ['D4', 'F4', 'A4', 'C5'], ['G3', 'B3', 'D4', 'F#4'], ['C4', 'E4', 'G4', 'B4']]
song('nuit', 'Nuit au lobby - composition originale', 6, [
    ('chime', 0.45, night),
    ('bass', 0.6, ' | '.join(c[0] + ':6 ' + c[0] + ':2' for c in lofi)),
    ('harp', 0.35, ' | '.join(c[1] + '+' + c[2] + '+' + c[3] + ':3 ' + c[1] + '+' + c[2] + '+' + c[3] + ':5' for c in lofi)),
    ('basedrum', 0.35, 'C4:3 C4:5'),
    ('hat', 0.12, '_ C5 _ C5 _ C5 _ C5'),
], repeat=2)

# ---------------------------------------------------------------- écriture
out = ['# Genere par gen_songs.py (ne pas modifier a la main : regenerer).', 'songs:']
for key, name, tempo, repeat, tracks in SONGS:
    out.append('  %s:' % key)
    out.append('    name: "%s"' % name)
    out.append('    tempo: %d' % tempo)
    out.append('    repeat: %d' % repeat)
    out.append('    tracks:')
    for inst, vol, notes in tracks:
        if vol <= 0:
            continue
        out.append('      - instrument: %s' % inst)
        out.append('        volume: %s' % vol)
        out.append('        notes: "%s"' % ' '.join(notes.split()))
    secs = length(tracks[0][2]) * tempo * repeat / 20
    print('%-14s %5.0f s' % (key, secs))
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src', 'main', 'resources', 'songs.yml')
open(path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
