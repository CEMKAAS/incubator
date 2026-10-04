# Собрать демо-базу на сегодняшнюю дату эмулятора и подменить ею базу приложения (debug-сборка).
cd "$(dirname "$0")" && source ui.sh
SQ=D:/Programms/Android/Sdk/platform-tools/sqlite3.exe
TODAY=$($ADB shell date +%d.%m.%Y | tr -d '\r')
node make_demo.js $TODAY > demo.sql && rm -f demo.db && $SQ demo.db < demo.sql
$ADB shell am force-stop $PKG
$ADB push demo.db /data/local/tmp/demo.db >/dev/null
$ADB shell "chmod 644 /data/local/tmp/demo.db; run-as $PKG sh -c 'rm -f databases/incubator_database*; cp /data/local/tmp/demo.db databases/incubator_database'"
demoBar
$ADB shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
