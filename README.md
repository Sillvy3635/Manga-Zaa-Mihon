# ส่วนขยายมังงะภาษาไทยสำหรับ Mihon

Extension Store ส่วนตัวสำหรับ Mihon และแอปที่รองรับ TachiyomiX extension library 1.6 ปัจจุบันมี:

- [Manga-Zaa](https://manga-zaa.net/)
- [BKK Manga](https://bkkmanga.com/)

## ความสามารถ

- รายการยอดนิยมและอัปเดตล่าสุด พร้อมแบ่งหน้า
- ค้นหาด้วยชื่อเรื่อง
- ข้อมูลเรื่อง ปก ผู้แต่ง ผู้วาด ประเภท สถานะ และชื่ออื่น
- รายการตอนพร้อมวันที่ภาษาไทย
- อ่านและดาวน์โหลดรูปทุกหน้า
- เปิดลิงก์ `/manga/...` เข้าสู่ Mihon โดยตรง

## Build ในเครื่อง

ต้องใช้ JDK 17 และ Android SDK:

```bash
./gradlew \
  :src:th:mangazaa:lintRelease :src:th:mangazaa:assembleRelease \
  :src:th:bkkmanga:lintRelease :src:th:bkkmanga:assembleRelease
```

APK จะอยู่ใน `src/th/<ชื่อโมดูล>/build/outputs/apk/release/`

## เปิดใช้ Extension Store บน GitHub

1. สร้าง GitHub repository แล้ว push โปรเจกต์นี้ไปยัง branch `main`
2. สร้าง signing key สำหรับใช้กับ Extension นี้โดยเฉพาะ:

   ```bash
   keytool -genkeypair -v -keystore signingkey.jks -alias mangazaa \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

3. เพิ่ม GitHub Actions secrets ต่อไปนี้:

   - `SIGNING_KEY`: เนื้อหา `signingkey.jks` ที่เข้ารหัส Base64 บรรทัดเดียว
   - `ALIAS`: `mangazaa`
   - `KEY_STORE_PASSWORD`: รหัสผ่าน keystore
   - `KEY_PASSWORD`: รหัสผ่าน key

   Linux/macOS:

   ```bash
   base64 -w 0 signingkey.jks
   ```

   PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("signingkey.jks"))
   ```

4. เปิด Actions แล้วรัน workflow `Build and publish store` หนึ่งครั้ง ระบบจะสร้าง GitHub Release และ branch `repo`
5. ใน Mihon ไปที่ `More → Settings → Browse → Extension stores` แล้วเพิ่ม:

   ```text
   https://raw.githubusercontent.com/USERNAME/REPOSITORY/repo/index.pb
   ```

ทุกครั้งที่แก้โค้ดของส่วนขยาย ให้เพิ่ม `versionCode` ใน `src/th/<ชื่อโมดูล>/build.gradle.kts` ก่อน push มิฉะนั้น Mihon จะไม่เห็นเป็นอัปเดตใหม่

> เก็บ `signingkey.jks` และรหัสผ่านไว้เป็นความลับและสำรองไว้อย่างปลอดภัย หากกุญแจสูญหาย จะออกอัปเดตทับ Extension เดิมไม่ได้

## ข้อจำกัดความรับผิดชอบ

โปรเจกต์นี้ไม่มีความเกี่ยวข้องกับ Mihon หรือเว็บไซต์ต้นทาง ผู้ใช้มีหน้าที่ปฏิบัติตามกฎหมายและเงื่อนไขของเว็บไซต์ที่เกี่ยวข้อง
