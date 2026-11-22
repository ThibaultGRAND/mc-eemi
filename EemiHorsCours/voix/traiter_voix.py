"""Voix de la Moustache : découpe l'enregistrement en 72 répliques et applique l'effet de voix.

Usage : python -I traiter_voix.py <enregistrement.mp3|wav|ogg> <sortie_dir> [--libs <dossier numpy/scipy/soundfile>]

- Découpe sur les silences (on cherche l'écart minimal qui donne exactement 72 morceaux).
- Répliques 1 à 62 : « voix au talkie » (plus grave de 3 demi-tons, sous-voix une octave plus bas,
  bande passante de radio, légère saturation, petite réverbération, souffle de talkie au début et à la fin).
- Répliques 63 à 72 (révélation) : « vraie voix » (plus grave de 5 demi-tons, grosse sous-voix,
  modulation, saturation, grande réverbération).
- Écrit <sortie>/NN.ogg (Vorbis, mono, 44,1 kHz), <sortie>/durees.txt (NN|ms) et un rapport.
"""
import os
import sys
import warnings

warnings.filterwarnings("ignore")

if "--libs" in sys.argv:
    sys.path.insert(0, sys.argv[sys.argv.index("--libs") + 1])

import numpy as np
import soundfile as sf
from scipy import signal

N_LINES = 72
REVEAL_FROM = 63
SR = 44100
ICI = os.path.dirname(os.path.abspath(__file__))


# ---------------------------------------------------------------- lecture et découpe
def load(path):
    x, sr = sf.read(path, always_2d=True)
    x = x.mean(axis=1).astype(np.float64)
    if sr != SR:
        g = np.gcd(sr, SR)
        x = signal.resample_poly(x, SR // g, sr // g)
    x -= x.mean()
    return x / (np.abs(x).max() + 1e-9)


def segments(x, gap):
    hop = int(0.02 * SR)
    frames = len(x) // hop
    rms = np.sqrt(np.mean(x[:frames * hop].reshape(frames, hop) ** 2, axis=1) + 1e-12)
    db = 20 * np.log10(rms)
    floor = np.percentile(db, 15)
    thr = max(floor + 12, db.max() - 45)
    on = db > thr
    segs, start = [], None
    for i, v in enumerate(on):
        if v and start is None:
            start = i
        elif not v and start is not None:
            segs.append([start, i])
            start = None
    if start is not None:
        segs.append([start, frames])
    # fusion des trous plus courts que gap
    merged = []
    for s in segs:
        if merged and (s[0] - merged[-1][1]) * 0.02 < gap:
            merged[-1][1] = s[1]
        else:
            merged.append(s)
    merged = [s for s in merged if (s[1] - s[0]) * 0.02 >= 0.3]
    pad = int(0.12 / 0.02)
    return [(max(0, a - pad) * hop, min(frames, b + pad) * hop) for a, b in merged]


def split(x, lengths):
    """Regroupe les morceaux de parole en len(lengths) répliques : programmation dynamique qui cherche
    des coupures sur de longs silences et des durées proches de la longueur du texte de chaque réplique."""
    s = segments(x, 0.35)
    m, n = len(s), len(lengths)
    if m < n:
        return None, s
    gaps = [0.0] + [(s[k][0] - s[k - 1][1]) / SR for k in range(1, m)]
    speech = sum((b - a) / SR for a, b in s)
    rate = speech / sum(lengths)
    INF = float("inf")
    C = [[INF] * (m + 1) for _ in range(n + 1)]
    P = [[0] * (m + 1) for _ in range(n + 1)]
    C[0][0] = 0.0
    for i in range(1, n + 1):
        e = max(0.6, rate * lengths[i - 1])
        for j in range(i, m + 1):
            for k in range(i - 1, j):
                if C[i - 1][k] == INF:
                    continue
                d = (s[j - 1][1] - s[k][0]) / SR
                c = C[i - 1][k] + ((d - e) / e) ** 2 - 0.6 * gaps[k]
                if c < C[i][j]:
                    C[i][j] = c
                    P[i][j] = k
    bounds, j = [m], m
    for i in range(n, 0, -1):
        j = P[i][j]
        bounds.append(j)
    bounds.reverse()
    return rate, [(s[bounds[i]][0], s[bounds[i + 1] - 1][1]) for i in range(n)]


# ---------------------------------------------------------------- effets
def stretch(x, rate, n_fft=2048, hop=512):
    """Vocodeur de phase : change la durée (rate > 1 = plus court) sans changer la hauteur."""
    win = signal.get_window("hann", n_fft)
    f, t, Z = signal.stft(x, SR, window=win, nperseg=n_fft, noverlap=n_fft - hop, boundary=None, padded=True)
    steps = np.arange(0, Z.shape[1] - 1, rate)
    phase = np.angle(Z[:, 0])
    omega = 2 * np.pi * hop * np.arange(Z.shape[0]) / n_fft
    out = np.zeros((Z.shape[0], len(steps)), dtype=complex)
    for k, s in enumerate(steps):
        i = int(s)
        frac = s - i
        a, b = Z[:, i], Z[:, i + 1]
        mag = (1 - frac) * np.abs(a) + frac * np.abs(b)
        out[:, k] = mag * np.exp(1j * phase)
        d = np.angle(b) - np.angle(a) - omega
        d -= 2 * np.pi * np.round(d / (2 * np.pi))
        phase += omega + d
    _, y = signal.istft(out, SR, window=win, nperseg=n_fft, noverlap=n_fft - hop, boundary=False)
    return y


def pitch(x, semis):
    """Change la hauteur sans changer la durée (étirement puis rééchantillonnage)."""
    f = 2 ** (semis / 12)
    y = stretch(x, 1 / f)
    n = len(x)
    y = signal.resample(y, max(1, int(len(y) / f)))
    return np.pad(y, (0, max(0, n - len(y))))[:n]


def band(x, lo, hi, order=4):
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def low(x, hz, order=4):
    return signal.sosfilt(signal.butter(order, hz, btype="low", fs=SR, output="sos"), x)


def high(x, hz, order=2):
    return signal.sosfilt(signal.butter(order, hz, btype="high", fs=SR, output="sos"), x)


def reverb(x, wet, size=1.0):
    """Petite réverbération de Schroeder (4 filtres en peigne + 2 passe-tout)."""
    out = np.zeros(len(x) + int(SR * 1.2 * size))
    xx = np.pad(x, (0, len(out) - len(x)))
    for d, g in ((0.0297, 0.77), (0.0371, 0.75), (0.0411, 0.73), (0.0437, 0.71)):
        n = int(d * size * SR)
        b = np.zeros(n + 1)
        b[0] = 1
        a = np.zeros(n + 1)
        a[0] = 1
        a[n] = -g
        out += signal.lfilter(b, a, xx)
    for d, g in ((0.005, 0.7), (0.0017, 0.7)):
        n = int(d * SR)
        b = np.zeros(n + 1)
        b[0] = -g
        b[n] = 1
        a = np.zeros(n + 1)
        a[0] = 1
        a[n] = -g
        out = signal.lfilter(b, a, out)
    out /= np.abs(out).max() + 1e-9
    return xx * (1 - wet) + out * wet * np.abs(xx).max()


def sat(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def squelch(ms, rng):
    n = int(SR * ms / 1000)
    s = band(rng.standard_normal(n), 900, 4500) * 0.25
    env = np.linspace(1, 0, n) ** 1.5
    return s * env


def fade(x, ms=12):
    n = min(len(x) // 2, int(SR * ms / 1000))
    e = np.ones(len(x))
    e[:n] = np.linspace(0, 1, n)
    e[-n:] = np.linspace(1, 0, n)
    return x * e


def norm(x, peak=0.89):
    return x / (np.abs(x).max() + 1e-9) * peak


def guide(x, rng):
    v = pitch(x, -3)
    sub = pitch(x, -12)
    y = v + 0.22 * sub
    radio = band(y, 280, 3600)
    y = 0.72 * radio + 0.28 * low(y, 6000)
    y = sat(norm(y), 1.8)
    y = reverb(y, 0.14, 0.7)
    y = norm(fade(y))
    return np.concatenate([squelch(90, rng), np.zeros(int(0.03 * SR)), y, np.zeros(int(0.02 * SR)), squelch(140, rng)])


def reveal(x, rng):
    v = pitch(x, -5)
    sub = pitch(x, -12)
    y = v + 0.5 * sub
    t = np.arange(len(y)) / SR
    y = 0.8 * y + 0.2 * y * np.sin(2 * np.pi * 38 * t)   # modulation qui « grince »
    y = high(y, 60)
    y = sat(norm(y), 3.0)
    y = reverb(y, 0.32, 1.6)
    return norm(fade(y), 0.95)


# ---------------------------------------------------------------- principal
def main():
    src, out = sys.argv[1], sys.argv[2]
    count = int(sys.argv[sys.argv.index("--n") + 1]) if "--n" in sys.argv else N_LINES
    os.makedirs(out, exist_ok=True)
    x = load(src)
    texts = {}
    vp = os.path.join(ICI, "..", "src", "main", "resources", "voix.txt")
    for l in open(vp, encoding="utf-8"):
        if "|" in l:
            k, v = l.split("|", 1)
            texts[int(k)] = v.strip()
    lengths = [len(texts.get(i + 1, "")) for i in range(count)]
    rate, segs = split(x, lengths)
    report = [f"Enregistrement : {len(x) / SR:.1f} s, repliques 1 a {count}"]
    if rate is None:
        report.append(f"Seulement {len(segs)} morceaux de parole trouves pour {count} repliques : rien n'est ecrit.")
        print(chr(10).join(report))
        sys.exit(2)
    durs = [(b - a) / SR for a, b in segs]
    corr = np.corrcoef(durs, lengths)[0, 1]
    report.append(f"Correlation duree / longueur du texte : {corr:.2f} (proche de 1 = bon alignement)")
    rng = np.random.default_rng(7)
    lines = []
    brut = os.path.join(out, "brut")
    os.makedirs(brut, exist_ok=True)
    for i, (a, b) in enumerate(segs):
        n = i + 1
        piece = x[a:b]
        y = reveal(piece, rng) if n >= REVEAL_FROM else guide(piece, rng)
        sf.write(os.path.join(out, f"{n:02d}.ogg"), y.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        # copie sans effet, nommée avec le début du texte : pour vérifier le découpage à l'oreille
        nom = "".join(c for c in texts.get(n, "")[:40] if c.isalnum() or c in " -'").strip()
        sf.write(os.path.join(brut, f"{n:02d} - {nom}.wav"), (piece * 0.9).astype(np.float32), SR)
        ms = int(len(y) / SR * 1000)
        lines.append(f"{n}|{ms}")
        report.append(f"  {n:02d}  {a / SR:6.1f} s  {durs[i]:5.1f} s (attendu {rate * lengths[i]:4.1f})  {texts.get(n, '')[:60]}")
    NL = chr(10)
    open(os.path.join(out, "durees.txt"), "w", encoding="utf-8").write(NL.join(lines) + NL)
    open(os.path.join(out, "rapport.txt"), "w", encoding="utf-8").write(NL.join(report) + NL)
    print(NL.join(report))
    print(f"{count} fichiers ecrits dans {out}")


if __name__ == "__main__":
    main()
