# BedWars1058-GeneralFixes 1.0.0

Fixes minor BedWars1058 gameplay bugs. Package: `dev.etran.generalfixes`. Kotlin 2.2.21, Gradle (Kotlin DSL).

Fixes (each has `fixes.<name>.enabled` in config.yml):
- cobwebs: removes the Halloween death cobweb
- chests: re-sends chest blocks so none are invisible at game start
- swords: removes outdated swords after a purchase
- tower: adds missing pop-up tower language entries

Command: `/bwfixes reload` (permission `bwfixes.admin`).

Build: `./gradlew allJars` -> `build/jars`. Uses the local `libs/bedwars-api-24.9.jar`; no remote BedWars repository is used.

## Developer note
This plugin is coded with AI assistance and architected completely by me. Regardless, use at your own risk.

## License
This plugin is under the MIT License, check the [License](LICENSE) file for more details.