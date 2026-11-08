# BedWars1058-GeneralFixes

Small gameplay fixes for [BedWars1058](https://github.com/andrei1058/BedWars1058). Minecraft 1.8 - 26.3.
Every fix is available as its own plugin jar, and all of them together as one jar.

| Jar | Fix | Command | Permission |
|---|---|---|---|
| `BedWars1058-NoDeathCobwebs` | Removes the cobweb BedWars1058's Halloween special places on every kill | `/cobwebfix reload` | `bwfixes.cobwebs.admin` |
| `BedWars1058-ChestVisibilityFix` | Re-sends chests so they are not invisible at game start | `/chestfix reload` | `bwfixes.chests.admin` |
| `BedWars1058-SwordReplacementFix` | Bought swords remove the weaker old swords | `/swordfix reload` | `bwfixes.swords.admin` |
| `BedWars1058-PopUpTowerLanguageFix` | Writes the missing pop-up tower name/lore into every language file | `/towerfix reload` | `bwfixes.tower.admin` |
| `BedWars1058-ShopNpcFix` | Shop villagers can no longer be damaged, and are respawned if they ever disappear | `/shopnpcfix reload` | `bwfixes.shopnpcs.admin` |
| `BedWars1058-OldLadderFix` | Pre-1.13 clients no longer glitch on ladders (needs a server newer than 1.8); 1.13+ clients are unaffected | `/ladderfix reload` | `bwfixes.oldladders.admin` |
| `BedWars1058-GeneralFixes` | All six above in one plugin | `/bwfixes reload` | `bwfixes.admin` |

Use either the combined jar **or** the individual jars, not both (they would do the same work twice).

Standalone jars have their options at the root of their `config.yml` (`enabled: true/false`). The combined jar uses
`fixes.<name>.enabled`. After editing, run the reload command, no restart needed.

## Build

    ./gradlew allJars      # -> build/jars/*.jar (all seven)
    ./gradlew :cobwebs:shadowJar   # just one, -> cobwebs/build/libs/

Kotlin 2.2.21, Java 8 bytecode, Kotlin runtime shaded and relocated. The code of all fixes lives in `fixes/`; each
other module is only a main class, `plugin.yml` and `config.yml`. The BedWars1058 API is taken from Maven
(`com.andrei1058.bedwars:bedwars-api`) or, if present, from `libs/bedwars-api.jar`.

## Developer note
This plugin is coded with AI assistance and architected completely by me. Regardless, use at your own risk.

## License
This plugin is under the MIT License, check the License file for more details.