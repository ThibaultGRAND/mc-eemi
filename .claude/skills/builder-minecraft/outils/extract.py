import sys, zipfile, os
zp, dest = sys.argv[1], sys.argv[2]
os.makedirs(dest, exist_ok=False)
z = zipfile.ZipFile(zp)
for i in z.infolist():
    n = i.filename
    if n.endswith('.schem') or n.endswith('locations.txt'):
        out = os.path.join(dest, os.path.basename(n))
        with open(out, 'wb') as f: f.write(z.read(i))
        print(out, i.file_size)
