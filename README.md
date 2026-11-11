# gan-designer-updates

ROM מותאם אישית ל‑Samsung Galaxy A21s (SM-A217F / SM-A217M), מבוסס על קוד המקור של LineageOS 22.2 (אנדרואיד 15).

השינויים נמצאים בתוך המערכת עצמה, בלי Magisk ובלי רוט.

## מה יש כאן

| נתיב | מה זה |
|---|---|
| `rom/vendor_extra/gan/allowed_packages.txt` | רשימת האפליקציות שמותר להתקין במכשיר |
| `rom/frameworks_base/.../GanInstallPolicy.java` | הקוד שבודק כל התקנה מול הרשימה |
| `rom/patches/` | השינוי במנהל החבילות של אנדרואיד שקורא לבדיקה |
| `scripts/` | סקריפטים להורדת הקוד, החלת השינויים ובנייה |

## איך החסימה עובדת

כל התקנה או עדכון של אפליקציה (מחנות, מקובץ APK או דרך ADB) עוברת בדיקה מול `allowed_packages.txt`. אפליקציה שלא ברשימה נדחית.

- הרשימה יושבת במחיצת המערכת, שהיא לקריאה בלבד. כדי לשנות אותה צריך לבנות ולצרוב ROM חדש.
- כל עוד הרשימה ריקה, **שום אפליקציה לא תותקן**. הוסף שמות חבילה לפני הבנייה.
- אפליקציות שבנויות בתוך ה‑ROM לא מושפעות מהרשימה.

## דרישות למחשב הבנייה

- אובונטו 22.04 או 24.04
- 300GB דיסק פנוי
- 16GB זיכרון לפחות (עדיף 32GB)

## בנייה

```bash
git clone https://github.com/awiawiawiawi/gan-designer-updates.git
cd gan-designer-updates

./scripts/1-setup-machine.sh    # פעם אחת: התקנת כלי הבנייה
./scripts/2-sync-source.sh      # הורדת קוד המקור (כמה שעות)
./scripts/4-build.sh            # החלת השינויים ובנייה (כמה שעות)
```

קוד המקור יורד ל‑`~/android/lineage`. אפשר לשנות את המיקום עם המשתנה `ROM_DIR`.

אחרי שינוי ברשימה או בקוד מריצים שוב רק את `4-build.sh`. בנייה חוזרת מהירה בהרבה.

## צריבה

בסוף הבנייה נוצרים בתיקייה `~/android/lineage/out/target/product/a21s/`:

- `recovery.img`
- `lineage-22.2-…-UNOFFICIAL-a21s.zip`

לפני הצריבה המכשיר צריך להיות עם קושחת סמסונג של אנדרואיד 12 ועם Bootloader פתוח. הצריבה מוחקת את כל המידע במכשיר.

1. מכבים את המכשיר ונכנסים למצב Download: מחזיקים את שני כפתורי הווליום ומחברים כבל USB למחשב.
2. צורבים את ה‑recovery עם Heimdall:
   ```bash
   heimdall flash --RECOVERY recovery.img --no-reboot
   ```
3. מנתקים את הכבל, מחזיקים Volume Down + Power עד שהמסך נכבה, ומיד עוברים ל‑Volume Up + Power כדי להיכנס ל‑recovery.
4. ב‑recovery בוחרים Factory reset ואז Format data / factory reset.
5. בוחרים Apply update ואז Apply from ADB, ומהמחשב:
   ```bash
   adb sideload lineage-22.2-*-UNOFFICIAL-a21s.zip
   ```
6. בוחרים Reboot system now.

## מצב הבדיקות

- הפאץ' נבדק מול קוד המקור של `lineage-22.2` ומוחל בלי שגיאות.
- `GanInstallPolicy.java` עבר קומפילציה בנפרד.
- בנייה מלאה של ה‑ROM והרצה על מכשיר עדיין לא נעשו.
