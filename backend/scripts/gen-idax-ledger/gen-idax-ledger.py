from pathlib import Path
import hashlib, json, yaml
HERE=Path(__file__).resolve().parent
BACKEND=HERE.parents[1]
FRONTEND=BACKEND.parent/"frontend"
cfg=yaml.safe_load((HERE/"table-config.yml").read_text(encoding="utf-8")); ui=cfg.get("module_ui",{})
menus=[{**m,"permission":"LEDGER_READ","group":ui.get("group","ledger"),"groupLabel":ui.get("groupLabel","ledger.title"),"groupIcon":ui.get("groupIcon","faLink"),"groupOrder":ui.get("groupOrder",38)} for m in ui.get("menus",[])]
routes=[{"path":m["route"],"permission":"LEDGER_READ"} for m in ui.get("menus",[])]
outputs={
 BACKEND/"src/main/resources/generated/ledger/permission-catalog.generated.json": json.dumps(cfg.get("permissions",[]),ensure_ascii=False,indent=2)+"\n",
 FRONTEND/"src/generated/ledger/manifest.generated.json": json.dumps({"module":"ledger","productName":ui.get("productName","IDAX Ledger"),"menus":menus,"routes":routes,"permissions":cfg.get("permissions",[]),"entities":sorted(cfg.get("entities",{}))},indent=2)+"\n",
 FRONTEND/"src/generated/ledger/crudCatalog.generated.json": "[]\n",
}
for path,content in outputs.items(): path.parent.mkdir(parents=True,exist_ok=True); path.write_text(content,encoding="utf-8",newline="\n")
for locale in ['es', 'en', 'ca', 'eu', 'gl', 'de', 'fr', 'it', 'pt', 'pt-BR', 'jp', 'zh']:
 path=FRONTEND/"src/locales"/f"{locale}.json"; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps({"ledger":{"title":"IDAX Ledger"}},ensure_ascii=False,sort_keys=True,indent=2)+"\n",encoding="utf-8",newline="\n")
for area in (BACKEND/"src/main/java/es/idynamicsax/ledger/generated",BACKEND/"src/main/java/es/idynamicsax/ledger/custom"): area.mkdir(parents=True,exist_ok=True); (area/".gitkeep").touch()
files={str(p.relative_to(BACKEND.parent)).replace("\\","/"):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(outputs)}
(BACKEND/".generated-manifest.json").write_text(json.dumps({"generatorVersion":1,"metadata":"scripts/gen-idax-ledger/table-config.yml","files":files},indent=2,sort_keys=True)+"\n",encoding="utf-8",newline="\n")
