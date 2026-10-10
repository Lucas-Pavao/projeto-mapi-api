"""Remove zooms abaixo de MIN do MBTiles e ajusta metadata (chamado por run.sh tiles). Uso: cortaz.py arquivo.mbtiles MIN"""
import sqlite3, sys
f, mn = sys.argv[1], int(sys.argv[2])
c = sqlite3.connect(f)
c.execute('DELETE FROM tiles WHERE zoom_level < ?', (mn,))
c.execute("INSERT OR REPLACE INTO metadata(name,value) VALUES('minzoom',?)", (str(mn),))
c.commit()
print('zooms:', c.execute('SELECT zoom_level, COUNT(*) FROM tiles GROUP BY 1 ORDER BY 1').fetchall())
c.execute('VACUUM'); c.close()
