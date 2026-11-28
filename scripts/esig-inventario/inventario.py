"""Inventário do ESIG (onda 16, Fase 6.1): só metadados, sem mudar nada e sem abrir conteúdo por ponto.

    python3 -I scripts/esig-inventario/inventario.py <saída.json>

Para cada serviço: tipo, camadas, campos, se tem tempo (timeInfo), contagem de registros (returnCountOnly) e faixa dos
campos de data (outStatistics min/max). ImageServer: extensão, resolução, bandas, dimensão de tempo, capacidades
(exportImage), multidimensão e contagem do catálogo. Camadas que podem ter ocorrência de alagamento (CTTU) ficam só nos
metadados: o conteúdo vai para o armazenamento lacrado quando houver coleta (regra 1 do prompt).
"""
import json
import sys
import time
import urllib.parse
import urllib.request

BASE = "https://esigportal2.recife.pe.gov.br/arcgis/rest/services"
ALVOS = [("pgdbesig_esi2_MD_APAC_v2", "ImageServer"), ("pgdbesig_esi2_MD_DECEA", "ImageServer"),
         ("DADOS_CTTU", "MapServer"), ("Cttu_Análise_Vias_Recife", "MapServer"),
         ("COP/BASES_ESTACAOMETEOROLOGICA_CONSULTA", "MapServer"), ("INMET/BASES_INMET_ALERTAS_CONSULTA", "MapServer")]
UA = {"User-Agent": "mapi-pesquisa/1.0"}


def get(url, **params):
    params.setdefault("f", "json")
    u = url + "?" + urllib.parse.urlencode(params)
    for k in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=90) as r:
                return json.loads(r.read().decode("utf-8", "replace"))
        except Exception as e:  # noqa: BLE001
            err = str(e)
            time.sleep(2 * (k + 1))
    return {"erro": err}


def servicos_da_pasta(pasta):
    d = get(f"{BASE}/{urllib.parse.quote(pasta)}")
    return [(s["name"], s["type"]) for s in d.get("services", [])]


def camada(url, lyr):
    meta = get(f"{url}/{lyr['id']}")
    campos = [{"nome": f["name"], "tipo": f["type"].replace("esriFieldType", "")} for f in meta.get("fields") or []]
    out = {"id": lyr["id"], "nome": lyr.get("name"), "geometria": meta.get("geometryType"), "campos": campos,
           "tempo": meta.get("timeInfo"), "maxRecordCount": meta.get("maxRecordCount")}
    if meta.get("type") == "Feature Layer":
        c = get(f"{url}/{lyr['id']}/query", where="1=1", returnCountOnly="true")
        out["registros"] = c.get("count", c.get("erro"))
        datas = [f["nome"] for f in campos if f["tipo"] == "Date"]
        if datas:
            st = [{"statisticType": t, "onStatisticField": datas[0], "outStatisticFieldName": t} for t in ("min", "max")]
            s = get(f"{url}/{lyr['id']}/query", where="1=1", outStatistics=json.dumps(st))
            try:
                a = s["features"][0]["attributes"]
                out["faixa_" + datas[0]] = [time.strftime("%Y-%m-%d", time.gmtime(a["min"] / 1000)) if a.get("min") else None,
                                            time.strftime("%Y-%m-%d", time.gmtime(a["max"] / 1000)) if a.get("max") else None]
            except Exception:  # noqa: BLE001
                out["faixa_" + datas[0]] = s.get("erro", "sem estatística")
    return out


def mapserver(nome):
    url = f"{BASE}/{urllib.parse.quote(nome)}/MapServer"
    d = get(url)
    return {"servico": nome, "tipo": "MapServer", "descricao": (d.get("serviceDescription") or d.get("description") or "")[:300],
            "tempo": d.get("timeInfo"), "camadas": [camada(url, l) for l in d.get("layers", [])], "erro": d.get("erro")}


def imageserver(nome):
    url = f"{BASE}/{urllib.parse.quote(nome)}/ImageServer"
    d = get(url)
    out = {"servico": nome, "tipo": "ImageServer", "descricao": (d.get("serviceDescription") or d.get("description") or "")[:400],
           "extensao": d.get("extent"), "resolucao": [d.get("pixelSizeX"), d.get("pixelSizeY")], "bandas": d.get("bandCount"),
           "tipo_pixel": d.get("pixelType"), "tempo": d.get("timeInfo"), "capacidades": d.get("capabilities"),
           "multidimensional": d.get("hasMultidimensions"), "campos": [f["name"] for f in d.get("fields") or []],
           "erro": d.get("erro")}
    c = get(f"{url}/query", where="1=1", returnCountOnly="true")
    out["itens_catalogo"] = c.get("count", c.get("erro"))
    tf = [f for f in out["campos"] if any(k in f.lower() for k in ("date", "data", "time", "hora"))]
    if tf:
        st = [{"statisticType": t, "onStatisticField": tf[0], "outStatisticFieldName": t} for t in ("min", "max")]
        s = get(f"{url}/query", where="1=1", outStatistics=json.dumps(st))
        out["faixa_" + tf[0]] = s.get("features", [{}])[0].get("attributes") if s.get("features") else s.get("erro")
    k = get(f"{url}/query", where="1=1", outFields="*", returnGeometry="false", resultRecordCount=2,
            orderByFields=(tf[0] + " DESC") if tf else "")
    out["exemplo_atributos_catalogo"] = [f.get("attributes") for f in k.get("features", [])][:2]
    out["keyProperties"] = get(f"{url}/keyProperties")
    return out


def main(saida):
    res = {"consultado_em_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "base": BASE,
           "pasta_PainelChuvas": servicos_da_pasta("PainelChuvas"), "pasta_APAC": servicos_da_pasta("APAC"),
           "pasta_CEMADEN": servicos_da_pasta("CEMADEN"), "pasta_COP": servicos_da_pasta("COP"),
           "pasta_INMET": servicos_da_pasta("INMET"), "pasta_CTTU": servicos_da_pasta("CTTU"), "servicos": []}
    alvos = list(ALVOS) + [(f"{n}", t) for n, t in res["pasta_PainelChuvas"]]
    for nome, tipo in alvos:
        print("...", nome, tipo, flush=True)
        if tipo == "ImageServer":
            res["servicos"].append(imageserver(nome))
        elif tipo in ("MapServer", "FeatureServer"):
            res["servicos"].append(mapserver(nome) if tipo == "MapServer" else {"servico": nome, "tipo": tipo})
        else:
            res["servicos"].append({"servico": nome, "tipo": tipo})
    open(saida, "w", encoding="utf-8").write(json.dumps(res, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main(sys.argv[1])
