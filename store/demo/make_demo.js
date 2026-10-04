// Демонстрационная база для снимков в магазины: node make_demo.js <dd.MM.yyyy сегодня> > demo.sql
// Схема берётся из data/schemas/…/20.json, так что файл открывается приложением без миграций.
// Все имена, породы и цены вымышленные. Даты считаются от «сегодня» на эмуляторе.
const path = require('path');
const schema = require(path.resolve(__dirname, '../../data/schemas/ru.zaroslikov.incubator.data.local.InventoryDatabase/20.json'));

const [d, m, y] = (process.argv[2] || '').split('.').map(Number);
if (!y) { console.error('нужна дата dd.MM.yyyy'); process.exit(1); }
const today = new Date(Date.UTC(y, m - 1, d));
const fmt = (date) => [date.getUTCDate(), date.getUTCMonth() + 1].map((n) => String(n).padStart(2, '0')).join('.') + '.' + date.getUTCFullYear();
const ago = (days) => fmt(new Date(today.getTime() - days * 86400000));
const plus = (date, days) => { const [a, b, c] = date.split('.').map(Number); return fmt(new Date(Date.UTC(c, b - 1, a + days))); };

const out = [];
const q = (v) => (v === null || v === undefined ? 'NULL' : typeof v === 'number' ? String(v) : "'" + String(v).replace(/'/g, "''") + "'");
const ins = (table, row) => out.push(`INSERT INTO \`${table}\` (${Object.keys(row).map((k) => '`' + k + '`').join(',')}) VALUES (${Object.values(row).map(q).join(',')});`);

for (const e of schema.database.entities) {
  out.push(e.createSql.replace('${TABLE_NAME}', e.tableName) + ';');
  for (const i of e.indices || []) out.push(i.createSql.replace('${TABLE_NAME}', e.tableName) + ';');
}
for (const s of schema.database.setupQueries) out.push(s + ';');
out.push('PRAGMA user_version = 20;');

// --- Режимы по дням: [до какого дня, t, влажность, перевороты, проветриваний, минут] ---
const regimens = {
  'Курицы': { days: 21, steps: [[7, 37.8, 55, 4, 0, 0], [14, 37.6, 50, 4, 2, 5], [18, 37.4, 45, 4, 2, 15], [21, 37.2, 70, 0, 0, 0]], candling: [7, 11, 16] },
  'Перепела': { days: 17, steps: [[7, 37.7, 55, 4, 0, 0], [14, 37.7, 55, 4, 1, 5], [17, 37.2, 65, 0, 0, 0]], candling: [6, 13] },
  'Утки': { days: 28, steps: [[7, 38.0, 60, 4, 0, 0], [14, 37.8, 55, 4, 1, 10], [25, 37.6, 50, 6, 2, 15], [28, 37.2, 75, 0, 0, 0]], candling: [8, 14, 25] },
  'Индюки': { days: 28, steps: [[7, 37.8, 60, 4, 0, 0], [14, 37.6, 50, 4, 1, 5], [25, 37.5, 50, 4, 2, 10], [28, 37.2, 70, 0, 0, 0]], candling: [8, 14, 25] },
  'Гуси': { days: 30, steps: [[7, 37.8, 60, 4, 0, 0], [14, 37.8, 55, 4, 1, 15], [27, 37.5, 55, 6, 2, 20], [30, 37.2, 80, 0, 0, 0]], candling: [9, 15, 21] },
  'Цесарки': { days: 27, steps: [[13, 37.8, 60, 4, 0, 0], [24, 37.5, 50, 4, 1, 10], [27, 37.2, 70, 0, 0, 0]], candling: [8, 15, 24] },
};
const planOf = (type, day) => { const s = regimens[type].steps.find((x) => day <= x[0]); return { temp: s[1], damp: s[2], over: s[3], airingCount: s[4], airingTime: s[5] }; };

// Свой вид
ins('Custom_species', { _id: 1, Name: 'Цесарки' });
for (let day = 1; day <= 27; day++) ins('Custom_species_day', { speciesId: 1, day, ...planOf('Цесарки', day), candling: regimens['Цесарки'].candling.includes(day) ? 1 : 0 });

ins('User', { _id: 1, Name: 'Семён' });

const incubator = (row) => ins('Incubator', { Note: '', AutoTurn: 0, AutoAiring: 0, Hidden: 0, TariffNight: null, NightStart: '', NightEnd: '', ...row });
incubator({ _id: 1, Name: 'Блиц 72', Capacity: 72, Brand: 'Блиц', Model: 'Норма 72', Price: 14500, PowerWatts: 60, TariffDay: 6.2, TariffNight: 3.1, NightStart: '23:00', NightEnd: '07:00', Note: 'Стоит в летней кухне. Воду доливать раз в три дня, датчик влажности — у задней стенки.' });
incubator({ _id: 2, Name: 'Несушка 104', Capacity: 104, Brand: 'Несушка', Model: 'БИ-2М', Price: 6900, PowerWatts: 115, TariffDay: 6.2 });
incubator({ _id: 3, Name: 'Золушка 70', Capacity: 70, Brand: 'Золушка', Model: '2020', Price: 5200, PowerWatts: 75, TariffDay: 6.2 });

let batchId = 0, valueId = 0;
// seed-генератор, чтобы пересборка давала те же замеры
let seed = 7; const rnd = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
const jitter = (v, spread, digits) => +(v + (rnd() - 0.5) * 2 * spread).toFixed(digits);

/**
 * b: { inc, name, type, breed, eggs, start(daysAgo), price, perEgg, status: 'active'|'hatched'|'stopped',
 *      hatched, chick, endDay, reason, candling: {day: rejected}, measured: bool, today: [..] }
 */
function batch(b) {
  const id = ++batchId;
  const reg = regimens[b.type];
  const date = ago(b.start);
  const finished = b.status !== 'active';
  const candlingSum = Object.values(b.candling || {}).reduce((a, c) => a + c, 0);
  const endDay = b.status === 'hatched' ? reg.days : (b.endDay || 0);
  ins('Batch', {
    _id: id, Name: b.name, Type: b.type, Date: date, Egg_all: b.eggs,
    Egg_all_end: b.status === 'hatched' ? b.hatched : 0,
    Airing: 'false', Overturn: 'false', Archive: finished ? '1' : '0',
    Date_end: finished ? plus(date, endDay) : '', note: b.note || '', incubatorId: b.inc, Breed: b.breed || '',
    Price: b.price || 0, PricePerEgg: b.perEgg === false ? 0 : 1,
    EndReason: b.status === 'stopped' ? b.reason : '',
    ChickPrice: b.chick || 0, ChickPricePerHead: 1, Hidden: 0, Time: '08:00',
    Egg_rejected: b.status === 'hatched' ? b.eggs - b.hatched - candlingSum : (b.rejected || 0),
    PowerWatts: null, TariffDay: null, TariffNight: null, NightStart: '', NightEnd: '',
    TimeEnd: finished ? (b.status === 'hatched' ? '14:30' : '10:00') : '',
  });
  ins('Batch_species', { species: b.type, idPT: id });
  ins('Batch_time', { time: '08:00', idPT: id, note: 'Проверить температуру и влажность' });
  ins('Batch_time', { time: '20:00', idPT: id, note: 'Перевернуть яйца' });
  for (const [day, rejected] of Object.entries(b.candling || {})) ins('Batch_candling', { idPT: id, day: +day, date: plus(date, +day - 1), rejected });

  const currentDay = finished ? endDay : b.start + 1;
  for (let day = 1; day <= reg.days; day++) {
    const vid = ++valueId;
    const plan = planOf(b.type, day);
    ins('Batch_value', { id: vid, day, ...plan, note: b.notes && b.notes[day] ? b.notes[day] : '', idPT: id });
    if (!b.measured) continue;
    const mes = (row) => ins('Batch_measurement', { idValue: vid, over: null, airingCount: null, airingTime: null, note: '', groupId: null, ...row });
    if (day < currentDay) {
      mes({ time: '08:10', temp: jitter(plan.temp, 0.2, 1), damp: jitter(plan.damp, 3, 0), over: plan.over ? 2 : null });
      mes({ time: '20:05', temp: jitter(plan.temp, 0.2, 1), damp: jitter(plan.damp, 3, 0), over: plan.over ? 2 : null, airingCount: plan.airingCount ? 1 : null, airingTime: plan.airingCount ? plan.airingTime : null });
    } else if (day === currentDay && b.today) {
      for (const t of b.today) mes(t);
    }
  }
  return id;
}

// --- Блиц 72: идущая закладка, вокруг которой сняты «сегодня», график, таймер и QR ---
batch({
  inc: 1, name: 'Осенняя закладка', type: 'Курицы', breed: 'Ломан Браун', eggs: 60, start: 13, price: 35, status: 'active',
  candling: { 7: 4, 11: 2 }, measured: true, note: 'Яйцо от своих несушек, собрано за пять дней.',
  notes: { 14: 'Долить воду в поддон' },
  today: [
    { time: '08:15', temp: 37.5, damp: 51, over: 1 },
    { time: '10:40', temp: 37.7, damp: 50, airingCount: 1, airingTime: 5 },
    { time: '13:20', temp: 37.6, damp: 48, over: 1, note: 'Долил воду в поддон' },
    { time: '16:05', temp: 37.8, damp: 52, airingCount: 1, airingTime: 5 },
    { time: '18:30', temp: 37.6, damp: 51, over: 1 },
    { time: '20:10', temp: 37.4, damp: 48, over: 1 },
  ],
});
batch({ inc: 1, name: 'Летняя партия', type: 'Курицы', breed: 'Ломан Браун', eggs: 60, start: 75, price: 35, status: 'hatched', hatched: 51, chick: 150, candling: { 7: 4, 11: 2, 16: 1 }, measured: true });
batch({ inc: 1, name: 'Хайсекс на продажу', type: 'Курицы', breed: 'Хайсекс Браун', eggs: 48, start: 110, price: 40, status: 'hatched', hatched: 38, chick: 160, candling: { 7: 5, 11: 2 } });
batch({ inc: 1, name: 'Кучинские', type: 'Курицы', breed: 'Кучинская юбилейная', eggs: 36, start: 150, price: 30, status: 'stopped', endDay: 6, reason: 'Отключали свет на двое суток' });
batch({ inc: 1, name: 'Весенняя партия', type: 'Курицы', breed: 'Ломан Браун', eggs: 60, start: 185, price: 32, status: 'hatched', hatched: 53, chick: 140, candling: { 7: 3, 11: 1, 16: 1 } });

// --- Несушка 104: гуси в день первого овоскопирования и перепела ---
batch({ inc: 2, name: 'Гуси линдовские', type: 'Гуси', breed: 'Линда', eggs: 24, start: 8, price: 150, status: 'active', measured: true, today: [{ time: '08:20', temp: 37.8, damp: 56, over: 1 }] });
batch({ inc: 2, name: 'Перепела на мясо', type: 'Перепела', breed: 'Фараон', eggs: 60, start: 4, price: 12, status: 'active' });
batch({ inc: 2, name: 'Муларды', type: 'Утки', breed: 'Мулард', eggs: 30, start: 60, price: 90, status: 'hatched', hatched: 22, chick: 280, candling: { 8: 4, 14: 2 } });
batch({ inc: 2, name: 'Перепела', type: 'Перепела', breed: 'Техасский белый', eggs: 80, start: 95, price: 12, status: 'hatched', hatched: 61, chick: 60, candling: { 6: 9 } });
batch({ inc: 2, name: 'Гуси весенние', type: 'Гуси', breed: 'Линда', eggs: 40, start: 135, price: 150, status: 'hatched', hatched: 31, chick: 600, candling: { 9: 5, 15: 2 } });

// --- Золушка 70: закладка, у которой сегодня вышел срок, — для поздравления ---
batch({ inc: 3, name: 'Хайсекс для себя', type: 'Курицы', breed: 'Хайсекс Браун', eggs: 54, start: 21, price: 40, status: 'active', candling: { 7: 3, 11: 1, 16: 1 }, measured: true });
batch({ inc: 3, name: 'Бронзовые', type: 'Индюки', breed: 'Бронзовая широкогрудая', eggs: 20, start: 70, price: 180, status: 'hatched', hatched: 14, chick: 450, candling: { 8: 3, 14: 1 } });
batch({ inc: 3, name: 'Цесарки', type: 'Цесарки', breed: 'Загорская белогрудая', eggs: 30, start: 120, price: 60, status: 'hatched', hatched: 21, chick: 220, candling: { 8: 4, 15: 2 } });
batch({ inc: 3, name: 'Бройлеры', type: 'Курицы', breed: 'КОББ-500', eggs: 60, start: 48, price: 45, status: 'hatched', hatched: 52, chick: 130, candling: { 7: 4, 11: 2 } });

console.log(out.join('\n'));
