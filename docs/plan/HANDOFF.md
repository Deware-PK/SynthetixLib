# Handoff v3: SynthetixLib — planner chat (Cowork)

วันที่: 24 ต.ค. 2026 · เจ้าของ: Phakin (Deware, github.com/Deware-PK)
โฟลเดอร์: `D:\Minecraft\Plugins\SynthetixLib` (ต้องเชื่อมเข้าแชทใหม่) · branch `main`

## บทบาทของแชทนี้
- **Claude ใน Cowork = planner/reviewer**: design (docs/design), ADR, แตก task, รีวิว diff, เลือก effort
- **Claude Code + Sonnet = executor**: Phakin ก๊อป "Prompt" ใน task ไปวาง แล้วส่ง Result กลับมารีวิว
- Task/prompt/design เขียน**อังกฤษ**; คุยกับ Phakin **ไทย กระชับ**
- Planner แก้ได้เฉพาะ docs/plan, docs/design, docs/adr; ห้ามแก้โค้ด ห้าม commit (wrap-up task `Ship: yes` → agent รัน `scripts/ship-module.ps1`)
- รีวิว = `git status`/`git diff` + Result ของ task + `synthetixlib-example/run/smoke-*/selftest-result.json`
- เครื่องฝั่ง Cowork build ไม่ได้ (ไม่มี JDK/Maven) — ผล build มาจาก Claude Code/IntelliJ
- อ่าน signature ของ paper-api ได้จาก `synthetixlib-example/run/1.21.11/libraries/.../paper-api-1.21.11-R0.1-SNAPSHOT.jar` (python parse class file; ไม่มี javap)

## สถานะ (24 ต.ค.)
| ส่วน | สถานะ |
|---|---|
| Phase 0 harness | ✅ |
| core | ✅ (P2-01..07) |
| config | ✅ 0.2.0 (P2-08..14) |
| command | ✅ 0.3.0, tag v0.3.0 (P2-15..18) |
| gui | ✅ 0.4.0, tag v0.4.0 (P2-19..23a) |
| database | ✅ 0.5.0, tag v0.5.0 (P2-24..28); owner เทสในเกมผ่าน 24 ต.ค. |
| dialog | ✅ 0.6.0 shipped, tag v0.6.0 (P2-29..33); รอ owner เทสในเกม + Geyser (P2-33 step 1-2) |
| docs (Phase 3) | ✅ 0.7.0 skill + JitPack (P3-01..06) |
| npc | ✖ ตัดออก (ADR 0016) — packet/PacketEvents ทำในโปรเจกต์ Farm Workers |

Smoke ล่าสุด: 88/88 PASS บน 1.21.11 และ 26.3

## ค้าง (25 ต.ค.)
- 0.8.0 shipped (c5786e8, tag v0.8.0): dialog number-field/inputRange/validate, command cooldown function/reset/TimeFormat,
  upgrade fixes (config-version warning, lang map keys). evals 0.8.0 3× 10/10 ใช้ API ใหม่เอง. JitPack v0.8.0: owner เช็ค
- ข้อเสนอถัดไป (ยังไม่ approve): `validateAsync` (shop-2), IA 2.0 เริ่มได้ (economy ทำในโปรเจกต์ ไม่ใส่ lib)
- dialog P2-33: owner เทสในเกมแล้วบางส่วน; Geyser (26.3) ยังค้าง → dialog.md §9

## Stack
Gradle 9.8.1 · build-logic `synthetix.java-base|library|paper-plugin` · versions ใน `gradle/libs.versions.toml`
paper-api 1.21.11-R0.1-SNAPSHOT · JDK 21 (compile) + 25 (Paper 26.x) · คำสั่ง `gradlew.bat check smokeTest runServer runServer26 publishToMavenLocal`
โมดูล: core, config, command, gui, database, example (test bed, package `com.github.dewarepk.synthetixexample`), archtest

## การตัดสินใจ → `docs/adr/0001..0014`, กติกาใน `AGENTS.md`
ไม่พึ่ง lib ภายนอก (ADR 0012) ยกเว้น HikariCP ที่ consumer โหลดผ่าน PluginLoader (ADR 0014); ไม่มี npc/packet (ADR 0016)

## Product ที่จะใช้ lib
InteractiveAuction 2.0 (core, config, command, gui, database, dialog + migrate 1.0) · Farm Workers (npc ใช้ PacketEvents ในโปรเจกต์เอง)

## สไตล์
ตอบไทย กระชับ · เสนอ default ให้ owner เลือกในหัวข้อ "Decisions for owner" · Phakin ชอบยืดหยุ่นแต่ใช้ง่าย ไม่พึ่งของนอก
