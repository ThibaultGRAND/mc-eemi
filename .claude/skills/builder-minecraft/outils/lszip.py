import sys, zipfile
for p in sys.argv[1:]:
    z = zipfile.ZipFile(p)
    names = z.infolist()
    print('==', p, len(names))
    for i in names[:12]: print('  ', i.filename, i.file_size)
