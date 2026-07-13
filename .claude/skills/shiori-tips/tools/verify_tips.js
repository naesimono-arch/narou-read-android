// 栞tip 実行時検証ハーネス。entries.js の各 draw を模擬2Dコンテキストで実走させ、硬制約を機械判定する。
// ①色は渡した a/pp のみ（色リテラル禁止）②canvasプリミティブのみ ③例外なし ＋ 包絡（正しいアフィン変換スタックで実座標判定）。
// 使い方: node verify_tips.js <entries.js> [constName=TIPS_BATCH1]
const fs = require('fs'), vm = require('vm');
const path = process.argv[2];
const constName = process.argv[3] || 'TIPS_BATCH1'; // batch2 は TIPS_Y/W/K/Z/H/F 等
const src = fs.readFileSync(path, 'utf8');

// 元カタログ由来ヘルパー（draw が call 時に参照する）。
function S(ctx,a){ctx.strokeStyle=a;ctx.lineCap='round';ctx.lineJoin='round';}
function star(ctx,cx,cy,rO,rI,n,rot){ctx.beginPath();for(let i=0;i<n*2;i++){const r=i%2?rI:rO,ang=rot+i*Math.PI/n;const px=cx+r*Math.cos(ang),py=cy+r*Math.sin(ang);i?ctx.lineTo(px,py):ctx.moveTo(px,py);}ctx.closePath();}

const ALLOWED = new Set(['beginPath','moveTo','lineTo','arc','arcTo','quadraticCurveTo','bezierCurveTo','rect','strokeRect','fill','stroke','closePath','save','translate','rotate','restore']);
const ALSO_STUB = ['ellipse','fillRect','clearRect','setTransform','scale','fillText','drawImage','createLinearGradient','createRadialGradient','clip','setLineDash','roundRect'];

// 2Dアフィン行列 [a,b,c,d,e,f]: X=a*x+c*y+e, Y=b*x+d*y+f。canvas と同じく現行行列に右から合成。
const I = [1,0,0,1,0,0];
function mul(M,T){const[a,b,c,d,e,f]=M,[a2,b2,c2,d2,e2,f2]=T;return[a*a2+c*b2,b*a2+d*b2,a*c2+c*d2,b*c2+d*d2,a*e2+c*f2+e,b*e2+d*f2+f];}

function makeCtx(A,P){
  const rec = {methods:new Set(), colors:[], xs:[], ys:[]};
  let M = I.slice(); const stack=[];
  const P2 = (lx,ly)=>{ rec.xs.push(M[0]*lx+M[2]*ly+M[4]); rec.ys.push(M[1]*lx+M[3]*ly+M[5]); };
  const ctx = {
    set fillStyle(v){rec.colors.push(v);}, get fillStyle(){return A;},
    set strokeStyle(v){rec.colors.push(v);}, get strokeStyle(){return A;},
    lineWidth:0, lineCap:'', lineJoin:'', font:'', textAlign:'', textBaseline:'',
  };
  const note=(nm,args)=>{
    rec.methods.add(nm);
    switch(nm){
      case 'save': stack.push(M.slice()); break;
      case 'restore': if(stack.length) M=stack.pop(); break;
      case 'translate': M=mul(M,[1,0,0,1,args[0],args[1]]); break;
      case 'rotate': {const c=Math.cos(args[0]),s=Math.sin(args[0]); M=mul(M,[c,s,-s,c,0,0]); break;}
      case 'scale': M=mul(M,[args[0],0,0,args[1],0,0]); break;
      case 'moveTo': case 'lineTo': P2(args[0],args[1]); break;
      case 'arc': {const[cx,cy,r]=args; P2(cx+r,cy); P2(cx-r,cy); P2(cx,cy+r); P2(cx,cy-r); break;}
      case 'rect': case 'strokeRect': {const[x,y,w,h]=args; P2(x,y);P2(x+w,y);P2(x,y+h);P2(x+w,y+h); break;}
      case 'quadraticCurveTo': P2(args[0],args[1]); P2(args[2],args[3]); break;
      case 'bezierCurveTo': P2(args[0],args[1]); P2(args[2],args[3]); P2(args[4],args[5]); break;
    }
  };
  for(const m of [...ALLOWED, ...ALSO_STUB]) ctx[m]=(...a)=>note(m,a);
  return {ctx,rec};
}

const sandbox = {S, star, Math, console};
vm.createContext(sandbox);
const famName = constName.replace(/^TIPS_/,'');
vm.runInContext(src + `\n; RESULT_TIPS=(typeof ${constName}!=="undefined")?${constName}:[]; RESULT_FAMS=(typeof FAMS_ADDED!=="undefined")?FAMS_ADDED:(typeof FAM_${famName}!=="undefined"?[FAM_${famName}]:[]);`, sandbox);
const tips = sandbox.RESULT_TIPS, fams = sandbox.RESULT_FAMS;

const A='__ACCENT__', P='__PAPER__';
let hardFail=0, envWarn=0;
const nmSeen=new Map();
console.log(`entries: ${tips.length}  FAMS_ADDED: ${JSON.stringify(fams)}`);
tips.forEach((t,i)=>{
  const id=constName.replace(/^TIPS_/,'')+(i+1);
  nmSeen.set(t.nm+'/'+t.rd,(nmSeen.get(t.nm+'/'+t.rd)||0)+1);
  const {ctx,rec}=makeCtx(A,P);
  let err=null;
  try{ t.draw(ctx, 0, 0, A, false, P); }catch(e){ err=e.message; }
  const badColors=[...new Set(rec.colors.filter(v=>v!==A && v!==P))];
  const badMethods=[...rec.methods].filter(m=>!ALLOWED.has(m));
  const xmax=Math.max(0,...rec.xs.map(Math.abs)), ymin=Math.min(0,...rec.ys), ymax=Math.max(0,...rec.ys);
  const envBad = xmax>13 || ymin<-8 || ymax>30; // 原点(0,0)基準・x±8/y+24 に余裕を見た閾値
  const problems=[];
  if(err) problems.push('THROW: '+err);
  if(badColors.length) problems.push('COLOR: '+JSON.stringify(badColors));
  if(badMethods.length) problems.push('METHOD: '+badMethods.join(','));
  if(err||badColors.length||badMethods.length) hardFail++;
  if(envBad){ envWarn++; problems.push(`ENV x±${xmax.toFixed(1)} y[${ymin.toFixed(1)},${ymax.toFixed(1)}]`); }
  if(problems.length) console.log(`  ${id} ${t.nm}: ${problems.join(' | ')}`);
});
const dups=[...nmSeen].filter(([,n])=>n>1);
console.log('---');
console.log(`HARD FAILS (色/メソッド/例外): ${hardFail}/${tips.length}`);
console.log(`ENV warnings (要目視): ${envWarn}/${tips.length}`);
console.log(`重複 nm/rd: ${dups.length? JSON.stringify(dups):'なし'}`);
console.log(hardFail===0 ? 'HARD OK ✅' : 'HARD VIOLATION ❌');
