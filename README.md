# BedWars1058-GeneralFixes 1.1.0

Splits the build: besides the combined plugin, every fix is now also built as its own plugin jar so admins can install only the fixes they want.

Small gameplay fixes for [BedWars1058](https://github.com/andrei1058/BedWars1058). Minecraft 1.8 - 26.3.

Every fix is available as its own plugin jar, and all of them together as one jar.

| Jar | Fix | Command | Permission |
|---|---|---|---|
| `BedWars1058-NoDeathCobwebs` | Removes the cobweb BedWars1058's Halloween special places on every kill | `/cobwebfix reload` | `bwfixes.cobwebs.admin` |
| `BedWars1058-ChestVisibilityFix` | Re-sends chests so they are not invisible at game start | `/chestfix reload` | `bwfixes.chests.admin` |
| `BedWars1058-SwordReplacementFix` | Bought swords remove the weaker old swords | `/swordfix reload` | `bwfixes.swords.admin` |
| `BedWars1058-PopUpTowerLanguageFix` | Writes the missing pop-up tower name/lore into every language file | `/towerfix reload` | `bwfixes.tower.admin` |
| `BedWars1058-GeneralFixes` | All 4 above in one plugin, each toggled with `fixes.<name>.enabled` | `/bwfixes reload` | `bwfixes.admin` |

Use either the combined jar **or** the individual jars, not both (they would do the same work twice).

Standalone jars have their options at the root of their `config.yml` (`enabled: true/false`). After editing, run the reload command, no restart needed.

## Build

    ./gradlew allJars      # -> build/jars/*.jar

Kotlin 2.2.21 (all modules), Java 8 bytecode, Kotlin runtime shaded and relocated under `dev.etran`. The BedWars1058 API is **never fetched from a remote repository**: it is compiled against the local `libs/bedwars-api-24.9.jar`. The code of all fixes lives in `fixes/`; every other module is only a main class, `plugin.yml` and `config.yml`.

## Developer note
This plugin is coded with AI assistance and architected completely by me. Regardless, use at your own risk.

## License
This plugin is under the MIT License, check the [License](LICENSE) file for more details.