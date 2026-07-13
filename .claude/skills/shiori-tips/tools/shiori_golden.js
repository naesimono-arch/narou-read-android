// 栞ゴールデン再生成ツール。正本 JS（bookshelf-shiori-grid-D.html の hashStr/mulberry32/param生成）を
// そのまま移植し、ShioriGeneratorTest の期待値（hue/xFrac/lenFrac/tipIndex）を tipCount 指定で算出する。
// なぜ: tipCount が変わると tipIndex が全 title で変わる → 採用数確定後に本スクリプトで golden を再生成する。
function hashStr(s){let h=2166136261>>>0;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619);}return h>>>0;}
function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296;};}
function rand(rng,lo,hi){return lo+(hi-lo)*rng();}
const PALETTE=[20,70,140,175,200,210,260,330];
function coverHue(title){return PALETTE[Math.floor(mulberry32(hashStr(title))()*PALETTE.length)];}
// Kotlin 側と同じく xFrac/lenFrac は Float 化される点に注意（比較は 1e-4 許容）。
function params(title,tipCount){
  const hue=coverHue(title);
  const rng=mulberry32(hashStr(title+'|B'));
  const xFrac=Math.fround(rand(rng,0.14,0.36));
  const lenFrac=Math.fround(rand(rng,0.30,0.60));
  const tipIndex=Math.floor(rng()*tipCount);
  return {hue,xFrac,lenFrac,tipIndex};
}

const tipCount=Number(process.argv[2]||31);
// ShioriGeneratorTest が固定している 3 title（ハッシュ検証用に生ハッシュも出す）。
const titles=['テスト','星降る夜のパン屋と魔法使い','黒の魔王と契約した俺、気づけば最強の従者に'];
// hash は tipCount 非依存＝不変。参考に signed int32 でも出す（Kotlin の shioriHash は Int）。
const toI32=u=>u|0;
console.log('tipCount =',tipCount);
console.log('hash(テスト) =',toI32(hashStr('テスト')),' hash(テスト|B) =',toI32(hashStr('テスト|B')));
for(const t of titles){
  const p=params(t,tipCount);
  console.log(`${t}\n  hue=${p.hue} tipIndex=${p.tipIndex} xFrac=${p.xFrac} lenFrac=${p.lenFrac}`);
}
