# Handoff v2: SynthetixLib — planner chat (Cowork)

วันที่: 14 ต.ค. 2026 · เจ้าของ: Phakin (Deware, github.com/Deware-PK)
โฟลเดอร์: `D:\Minecraft\Plugins\SynthetixLib` (ต้องเชื่อมเข้าแชทใหม่) · branch `development`

## บทบาทของแชทนี้ (สำคัญ)
- **Claude ใน Cowork = planner/reviewer**: ออกแบบ (docs/design), เขียน ADR, แตก task, รีวิว diff, เลือก effort
- **Claude Code + Sonnet = executor**: Phakin ก๊อป "Prompt" ในไฟล์ task ไปวาง แล้วส่ง Result/diff กลับมาให้รีวิว
- Task และ prompt **เขียนภาษาอังกฤษ** (ประหยัด token); คุยกับ Phakin **ภาษาไทย กระชับ**
- Task ที่มี `Approved: yes` → Claude Code ลงมือได้เลยไม่ต้องเสนอแผนก่อน (กติกาใน AGENTS.md)
- Planner แก้ได้เฉพาะ docs/plan/ADR; **ห้ามแก้โค้ดเองถ้าไม่ได้สั่ง, ห้าม commit** (Phakin commit เอง)
- รีวิว = อ่าน `git status`/`git diff` + ส่วน Result ของ task + ไฟล์ `synthetixlib-example/run/smoke-*/selftest-result.json` → ผ่านแล้วเปลี่ยน Status เป็น done, เจอปัญหาเขียน task polish ใหม่
- เครื่องฝั่ง Cowork **build ไม่ได้** (ไม่มี network ไป Maven) — ผล build ต้องมาจาก Claude Code/IntelliJ

## สถานะ
| ส่วน | สถานะ |
|---|---|
| Phase 0 harness | ✅ done (commit `90abc6c`) |
| core (P2-01..P2-07) | ✅ done, commit `6f26d48` "Done all CORE phase" (P2-07 polish อาจยังไม่ commit — เช็ค git status) |
| config design | ✅ approved (`docs/design/config.md`, ทั้ง 9 ข้อใช้ค่าที่เสนอ) |
| config tasks | 📝 เขียนแล้ว P2-08..P2-14 ยังไม่เริ่ม (ไฟล์ docs ยังไม่ commit) |
| ถัดไป | ส่ง P2-08 → … → P2-14; ระหว่างนั้น planner ร่าง `docs/design/command.md` |

Smoke ล่าสุด: 34/34 self-tests PASS บน Paper 1.21.11 และ 26.3

## Stack ที่ใช้จริง (ต่างจาก handoff v1)
- **Gradle wrapper 9.8.1** (เดิม 9.0.0 — shadow 9.6.1 ต้องการ Kotlin 2.4 metadata)
- build-logic convention: `synthetix.java-base` / `synthetix.library` / `synthetix.paper-plugin`; เวอร์ชันใน `gradle/libs.versions.toml`
- paper-api 1.21.11-R0.1-SNAPSHOT, run-paper 3.1.0, shadow 9.6.1, ArchUnit 1.5.1, JUnit 5.13.4
- JDK: 21 (`C:\Program Files\Java\jdk-21.0.10`) compile/daemon, 25 (`jdk-25.0.4`) สำหรับ Paper 26.x
- คำสั่ง: `gradlew.bat check` / `smokeTest` (บูต 1.21.11 + 26.3) / `runServer` / `runServer26` / `publishToMavenLocal`
- โมดูล: `synthetixlib-core`, `synthetixlib-example` (test bed + demo, package `com.github.dewarepk.synthetixexample`), `synthetixlib-archtest`

## การตัดสินใจทั้งหมด → `docs/adr/0001..0013`
สำคัญที่เพิ่มในแชทนี้:
- ADR 0005 consumer **shade + relocate** lib (ผู้ซื้อลงไฟล์เดียว) → ห้าม static state / Class.forName
- ADR 0011 publish: mavenLocal จนถึง 1.0.0 แล้วค่อย JitPack
- ADR 0012 **ไม่พึ่ง lib ภายนอกเลย ยกเว้น PacketEvents** (config เขียนเอง บน `YamlConfiguration` ของ Paper)
  - ⚠️ ค้าง: database เคยวาง HikariCP → ตัดสินตอนออกแบบ database (pool เขียนเอง vs consumer โหลด Hikari เองผ่าน PluginLoader)
- ADR 0013 reflection อนุญาตเฉพาะ package `config.mapping`

## core API (สรุป, ละเอียดใน docs/design/core.md)
`SynthetixLib.init(this)` → `scheduler()` (global/region/entity/async, Folia-safe, close() ยกเลิกทุก task), `text()` (MiniMessage, `with()` immutable), `items()` (ItemBuilder, build() คืน stack ใหม่), `keys()` (PdcKey typed) · factory `create()` ของแต่ละ service เป็น `@ApiStatus.Internal`

## config design (สรุป)
- record + annotation (`@Comment @Key @Range @NotEmpty @TypeName`), key แปลงเป็น kebab-case
- **Codecs** แยกชั้น ใช้ซ้ำนอก YAML ได้ (encode/decode, JSON ผ่าน Gson ของ Paper, sealed interface → `type:`), ItemStack อ่านง่ายหรือ base64, Location
- `configs.file("settings.yml", …)` ทั้งระบบ; `configs.collection("airdrops", …)` **ไฟล์ต่อ object** (create/update/rename/delete → `.deleted/`, `_template.yml`, onChange Added/Updated/Removed, เขียน atomic คิวเดียว)
- load: เติม key ที่ขาดพร้อมคอมเมนต์, ค่าผิด → default + error (ไม่เขียนทับ), reload ไม่เซฟซ้ำ, migration + backup
- locale `lang/<tag>.yml`, per-player, ข้อความว่าง = ปิด
- core จะได้ `lib.attach(AutoCloseable)` (P2-08)

## Task ของ config (effort ที่ planner เลือก)
| Task | งาน | Effort |
|---|---|---|
| P2-08 | ตั้งโมดูล + `lib.attach` + กฎ ArchUnit ADR 0013 | medium |
| P2-09 | Codec layer (record, generics, sealed, JSON) | **high** |
| P2-10 | Bukkit codecs (registry, Location, ItemStack) | medium |
| P2-11 | YAML file config + migration | **high** |
| P2-12 | Collections (ไฟล์ต่อ object) | **high** |
| P2-13 | Locale | medium |
| P2-14 | demo + docs + publish 0.2.0 | low |
ลำดับ: 08 → 09 → (10, 11 ขนานได้) → (12, 13) → 14

## ค้าง / สิ่งที่ Phakin ต้องทำเอง
- ทดสอบ demo มือ: `gradlew.bat runServer` เข้า localhost ต้องได้ไอเทม "Demo ticket" + ข้อความต้อนรับ
- commit docs ใหม่ (ADR 0012/0013, config.md, task P2-08..14)
- ยังไม่ได้สร้าง GitHub repo

## Product ที่จะใช้ lib (ไม่เปลี่ยนจาก v1)
InteractiveAuction 2.0 (ต้องการ core, config, command, gui, database + migrate ข้อมูล 1.0) · Farm Workers (+ npc)

## สไตล์
- ตอบไทย กระชับ; task/prompt อังกฤษ
- เสนอแผน/ถามก่อนตัดสินใจเรื่อง design; Phakin ชอบระบบยืดหยุ่นแต่ใช้ง่าย และไม่พึ่งของภายนอก
- ไม่ commit เอง
