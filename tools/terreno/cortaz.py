"""Remove zooms abaixo de MIN e acima de MAX (opcional) do MBTiles e ajusta metadata (chamado por run.sh tiles). Uso: cortaz.py arquivo.mbtiles MIN [MAX]"""
import sqlite3, sys
f, mn = sys.argv[1], int(sys.argv[2])
mx = int(sys.argv[3]) if len(sys.argv) > 3 else None
c = sqlite3.connect(f)
c.execute('DELETE FROM tiles WHERE zoom_level < ?', (mn,))
c.execute("INSERT OR REPLACE INTO metadata(name,value) VALUES('minzoom',?)", (str(mn),))
if mx is not None:
    c.execute('DELETE FROM tiles WHERE zoom_level > ?', (mx,))
    c.execute("INSERT OR REPLACE INTO metadata(name,value) VALUES('maxzoom',?)", (str(mx),))
c.commit()
print('zooms:', c.execute('SELECT zoom_level, COUNT(*) FROM tiles GROUP BY 1 ORDER BY 1').fetchall())
c.execute('VACUUM'); c.close()
