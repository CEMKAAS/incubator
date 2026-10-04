# Помощники для съёмки экранов с эмулятора: source ui.sh
export MSYS_NO_PATHCONV=1
ADB="D:/Programms/Android/Sdk/platform-tools/adb -s emulator-5554"
PKG=ru.zaroslikov.incubator

# Тексты и подписи на экране с координатами центра: "x y  текст"
dump() {
  $ADB shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  $ADB shell cat /sdcard/u.xml | perl -CSD -0ne '
    while (/<node ([^>]*)>/g) {
      my $a = $1; my ($t) = $a =~ / text="([^"]+)"/; my ($d) = $a =~ /content-desc="([^"]+)"/;
      my $n = $t // $d; next unless defined $n;
      next unless $a =~ /bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/;
      printf "%d %d  %s\n", ($1+$3)/2, ($2+$4)/2, $n;
    }'
}
# tapText "текст" [номер совпадения, с 1]
tapText() {
  local line
  line=$(dump | grep -F -- "$1" | sed -n "${2:-1}p")
  [ -z "$line" ] && { echo "нет на экране: $1" >&2; return 1; }
  $ADB shell input tap $(echo "$line" | cut -d' ' -f1,2)
  sleep "${3:-1.5}"
}
shot() { $ADB exec-out screencap -p > "$1"; }
swipeUp() { $ADB shell input swipe 540 ${1:-1700} 540 ${2:-900} 400; sleep 1; }
demoBar() {
  $ADB shell "settings put global sysui_demo_allowed 1
    am broadcast -a com.android.systemui.demo -e command enter >/dev/null
    am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930 >/dev/null
    am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
    am broadcast -a com.android.systemui.demo -e command network -e mobile hide >/dev/null
    am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true >/dev/null
    am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null"
}
