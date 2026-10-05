// Собирает обложки для магазинов: node listing.js <slides.json> <shotsDir> <outDir>
// Каждый слайд: { file, eyebrow, title, subtitle, shot, theme: 'light'|'dark'|'green', tilt?: -4..4, offset?: px }
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const [slidesFile, shotsDir, outDir] = process.argv.slice(2);
const slides = JSON.parse(fs.readFileSync(slidesFile, 'utf8'));
fs.mkdirSync(outDir, { recursive: true });
const here = path.resolve(__dirname).replace(/\\/g, '/');

const themes = {
  light: { bg: 'radial-gradient(120% 70% at 85% 10%, #E6F0EA 0%, rgba(230,240,234,0) 60%), radial-gradient(90% 60% at 10% 95%, #FBF1DA 0%, rgba(251,241,218,0) 60%), #FAF8F3', eyebrow: '#3F7D5C', title: '#2B2620', sub: '#6E675C', frame: '#2B2620', blob1: '#3F7D5C', blob2: '#B5811F', shadow: 'rgba(43,38,32,0.28)' },
  green: { bg: 'radial-gradient(110% 60% at 90% 0%, #4F9A72 0%, rgba(79,154,114,0) 60%), radial-gradient(90% 60% at 0% 100%, #1E3328 0%, rgba(30,51,40,0) 60%), #3F7D5C', eyebrow: '#CDEDA3', title: '#FFFFFF', sub: 'rgba(255,255,255,0.78)', frame: '#1B2A22', blob1: '#CDEDA3', blob2: '#FBF1DA', shadow: 'rgba(0,0,0,0.38)' },
  dark: { bg: 'radial-gradient(110% 60% at 90% 0%, #1E3328 0%, rgba(30,51,40,0) 60%), radial-gradient(90% 60% at 0% 100%, #2A2622 0%, rgba(42,38,34,0) 60%), #161411', eyebrow: '#6DB48C', title: '#EDE7DC', sub: '#A39A8B', frame: '#3B3631', blob1: '#6DB48C', blob2: '#D4A03A', shadow: 'rgba(0,0,0,0.55)' },
};

function html(s) {
  const t = themes[s.theme || 'light'];
  const tilt = s.tilt || 0;
  const offset = s.offset ?? 0;
  const shot = path.resolve(shotsDir, s.shot).replace(/\\/g, '/');
  const eggs = (s.eggs ?? true) ? `
    <div class="egg e1"></div><div class="egg e2"></div><div class="egg e3"></div>` : '';
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8">
<link rel="stylesheet" href="file:///${here}/_fonts.css">
<style>
html,body{margin:0;width:1080px;height:1920px;overflow:hidden}
body{background:${t.bg};font-family:Inter,'Segoe UI',sans-serif;position:relative}
.egg{position:absolute;border-radius:50% 50% 50% 50%/60% 60% 40% 40%;opacity:.16;filter:blur(2px)}
.e1{width:520px;height:640px;left:-190px;top:1180px;background:${t.blob1};transform:rotate(-18deg)}
.e2{width:300px;height:380px;right:-90px;top:640px;background:${t.blob2};transform:rotate(14deg)}
.e3{width:160px;height:200px;right:130px;top:80px;background:${t.blob1};transform:rotate(30deg);opacity:.12}
.text{position:absolute;left:80px;right:80px;top:${s.textTop ?? 120}px;z-index:2}
.eyebrow{font-family:Comfortaa,sans-serif;font-size:26px;letter-spacing:.18em;text-transform:uppercase;color:${t.eyebrow};font-weight:600;margin-bottom:26px}
h1{font-family:Lora,Georgia,serif;font-weight:600;font-size:${s.titleSize ?? 84}px;line-height:1.04;color:${t.title};margin:0 0 26px;letter-spacing:-.01em}
p{font-size:34px;line-height:1.35;color:${t.sub};margin:0;max-width:900px;font-weight:500}
.phone{position:absolute;left:50%;top:${s.phoneTop ?? 560}px;width:${s.phoneW ?? 820}px;aspect-ratio:1080/2400;margin-left:${-(s.phoneW ?? 820) / 2 + offset}px;transform:rotate(${tilt}deg);transform-origin:50% 0;border-radius:74px;background:${t.frame};padding:16px;box-sizing:border-box;box-shadow:0 60px 120px ${t.shadow}, 0 10px 30px rgba(0,0,0,.18);z-index:1}
.screen{width:100%;height:100%;border-radius:60px;overflow:hidden;background:#000;position:relative}
.screen img{width:100%;display:block}
.notch{position:absolute;left:50%;top:18px;width:40px;height:40px;margin-left:-20px;border-radius:50%;background:${t.frame};z-index:3;box-shadow:inset 0 0 0 3px rgba(255,255,255,.06)}
${s.badge ? `.badge{position:absolute;z-index:4;left:${s.badge.x}px;top:${s.badge.y}px;background:#FFFFFF;color:#2B2620;border-radius:32px;padding:22px 34px;font-size:30px;font-weight:600;box-shadow:0 20px 50px rgba(0,0,0,.25);display:flex;gap:16px;align-items:center;transform:rotate(${s.badge.tilt || -3}deg)}
.badge b{font-family:Comfortaa,sans-serif;color:#3F7D5C;font-size:34px}` : ''}
</style></head><body>${eggs}
<div class="text"><div class="eyebrow">${s.eyebrow}</div><h1>${s.title}</h1><p>${s.subtitle}</p></div>
<div class="phone"><div class="screen"><div class="notch"></div><img src="file:///${shot}"></div></div>
${s.badge ? `<div class="badge"><b>${s.badge.value}</b><span>${s.badge.label}</span></div>` : ''}
</body></html>`;
}

for (const s of slides) {
  const htmlPath = path.resolve(outDir, s.file.replace(/\.png$/, '.html'));
  fs.writeFileSync(htmlPath, html(s));
  const png = path.resolve(outDir, s.file);
  execFileSync(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', '--window-size=1080,1920', '--virtual-time-budget=4000', `--screenshot=${png}`, 'file:///' + htmlPath.replace(/\\/g, '/')], { stdio: 'ignore' });
  console.log('rendered', s.file);
}
