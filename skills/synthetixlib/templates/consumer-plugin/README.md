# MyPlugin

A Paper plugin built on SynthetixLib: a typed config, a language file, a command, a menu and a dialog.

- `/myplugin menu` — opens `menus/main.yml`; its button opens the greeting dialog.
- `/myplugin greet` — opens `dialogs/greet.yml`; the answer is read into a typed record.
- `/myplugin reload` — reloads `config.yml` and `lang/`.

Build: `./gradlew build` (JDK 21+). The jar is `build/libs/myplugin-1.0.0.jar`.
Contributors and AI agents: read AGENTS.md.
