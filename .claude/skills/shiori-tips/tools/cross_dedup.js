// 栞tip 横断dedup（汎用）。正本 shiori-tips-D.html の既存 nm/rd と、新 entries ファイルの nm/rd の
// 衝突を検出する。使い方: node cross_dedup.js <new-entries.js> <constName>
// 例: node cross_dedup.js docs/design-candidates/shiori-tips-batch3-foo.entries.js TIPS_X
const fs=require('fs'),vm=require('vm');
const newFile=process.argv[2], constName=process.argv[3];
if(!newFile||!constName){console.error('usage: node cross_dedup.js <entries.js> <constName>');process.exit(2);}
const SHOHON='/home/qingj/wt/ui-shiori-tips/docs/design-candidates/shiori-tips-D.html';
function S(){} function star(){}
function ev(code,cn){const sb={S,star,Math,console};vm.createContext(sb);vm.runInContext(code+`\n;OUT=(typeof ${cn}!=="undefined")?${cn}:[];`,sb);return sb.OUT;}

// 正本の既存 {nm,rd} を抽出（描画は実行しないので S/star はダミーで良い）。
const shohon=fs.readFileSync(SHOHON,'utf8');
const m=shohon.match(/const TIPS=\[([\s\S]*?)\];\nconst FAMS/);
if(!m) throw new Error('正本 TIPS 抽出失敗');
const existing=ev('const TIPS=['+m[1]+'];','TIPS').map(t=>({id:'正本',nm:t.nm,rd:t.rd}));
const added=ev(fs.readFileSync(newFile,'utf8'),constName).map((t,i)=>({id:constName.replace(/^TIPS_/,'')+(i+1),nm:t.nm,rd:t.rd}));
const all=existing.concat(added);
console.log(`正本 ${existing.length} ＋ 新規 ${added.length} ＝ ${all.length} を突合`);

function dup(key){const map=new Map();for(const e of all){const v=e[key];if(v==null)continue;if(!map.has(v))map.set(v,[]);map.get(v).push(e.id);}return [...map].filter(([,ids])=>ids.length>1);}
const nmDup=dup('nm'), rdDup=dup('rd');
console.log('--- nm 衝突 ---'); console.log(nmDup.length?nmDup.map(([v,ids])=>`  「${v}」: ${ids.join(', ')}`).join('\n'):'  なし');
console.log('--- rd 衝突（同音異字は別意匠なら許容） ---'); console.log(rdDup.length?rdDup.map(([v,ids])=>`  「${v}」: ${ids.join(', ')}`).join('\n'):'  なし');
process.exit(nmDup.length?1:0);
