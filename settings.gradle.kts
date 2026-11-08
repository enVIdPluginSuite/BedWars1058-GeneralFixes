rootProject.name = "BedWars1058-GeneralFixes"

// fixes    = library with the code of all four fixes (not a plugin itself)
// combined = BedWars1058-GeneralFixes.jar, all fixes in one plugin
// cobwebs / chests / swords / tower / shopnpcs / oldladders = one plugin jar per fix
include("fixes", "combined", "cobwebs", "chests", "swords", "tower", "shopnpcs", "oldladders")
