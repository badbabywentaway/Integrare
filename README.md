# Integrare

A custom item provider abstraction for Paper plugins. Write your plugin against one
small interface and let the server owner choose the item plugin:

| Provider   | Used when                                   |
|------------|---------------------------------------------|
| ItemsAdder | ItemsAdder is installed                     |
| Oraxen     | Oraxen is installed                         |
| Nexo       | Nexo is installed (untested, see below)     |
| NativeItems| none of the above — built-in fallback       |

Integrare is a library, not a plugin: shade it into your plugin jar.

Extracted from [CharmedChars](https://github.com/badbabywentaway/CharmedChars).

## Usage

```kotlin
class MyPlugin : JavaPlugin() {
    lateinit var items: CustomItemProviderManager

    override fun onEnable() {
        items = CustomItemProviderManager(this, nativeNbtKey = "myplugin_item_id")
        val result = items.initialize()
        if (!result.success) {
            result.messages.forEach { logger.severe(it) }
            server.pluginManager.disablePlugin(this)
            return
        }

        val provider = items.getProvider()!!
        val stack = provider.getItemStack("myplugin:ruby_block")
    }
}
```

`initialize()` fails if more than one of ItemsAdder, Oraxen, and Nexo is enabled.
Add them as `softdepend` in your `plugin.yml` so they load before your plugin.

### CustomItemProvider

| Method              | Purpose                                              |
|---------------------|------------------------------------------------------|
| `getItemStack`      | Build an `ItemStack` from a `namespace:name` ID      |
| `getCustomItem`     | Identify a custom item from an `ItemStack`           |
| `getCustomBlock`    | Identify a placed custom block                       |
| `removeCustomBlock` | Remove a placed custom block                         |
| `getProviderName`   | `ItemsAdder`, `Oraxen`, `Nexo`, or `NativeItems`     |
| `isAvailable`       | Whether the backing plugin is present                |

Oraxen and Nexo have no namespaces: the namespace is stripped on lookup, and
identified items come back as `oraxen:<id>` / `nexo:<id>`.

## Describing your items once: ItemCatalog

Describe your items and recipes once as an `ItemCatalog`, and Integrare writes
the configuration for whichever provider the server runs:

```kotlin
val catalog = ItemCatalog(
    namespace = "myplugin",
    items = listOf(
        CatalogItem.Block("ruby_block", "<red>Ruby Block", texture = "block/ruby"),
        CatalogItem.Item(
            "ruby_pickaxe", "<red>Ruby Pickaxe", texture = "item/ruby_pickaxe",
            baseMaterial = Material.GOLDEN_PICKAXE, handheld = true,
            durability = 250, miningTier = ToolTier.IRON, attackDamage = 4.0, attackSpeed = -2.8
        )
    ),
    recipes = listOf(
        CatalogRecipe.Shaped(
            "ruby_pickaxe", "ruby_pickaxe", listOf("RRR", " S ", " S "),
            mapOf('R' to Ingredient.Custom("ruby_block"), 'S' to Ingredient.Vanilla("STICK"))
        )
    ),
    // PNGs at <root>/<texture>.png in your jar; REDUCED is used where pack size matters
    textures = TextureSource.fromPluginJar(this, fullRoot = "textures", reducedRoot = "textures-256")
)
```

Then install it into the active provider, e.g. from an admin command:

```kotlin
val setup = ProviderSetup.forProvider(items.getProvider()!!, this, catalog) { native ->
    NativeSetup(catalog, native, dataFolder, logger, cmdBase = 1000,
        zipFileName = "MyPlugin-pack.zip", packDescription = "MyPlugin items")
}
if (setup != null && !setup.isAlreadySetup()) {
    val result = setup.install()
    result.messages.forEach { logger.info(it) }
    logger.info("Next: ${setup.nextSteps}")
}
```

| Setup             | Writes                                                                 |
|-------------------|------------------------------------------------------------------------|
| `ItemsAdderSetup` | `contents/<namespace>/` (blocks.yml, items.yml, textures), enables the namespace |
| `OraxenSetup`     | `items/<namespace>_blocks.yml`, appends to `recipes/shaped.yml`/`shapeless.yml`, models and textures in `pack/` |
| `NexoSetup`       | `items/<namespace>_blocks.yml`, `recipes/{shaped,shapeless}/<namespace>.yml`, `pack/assets/<namespace>/` |
| `NativeSetup`     | registers items and recipes, generates a resource pack zip            |

`install(force = true)` removes the previous installation first.

### Numbering is permanent

Oraxen custom variations, native custom model data and native note block states
are assigned in catalog order. Placed blocks and items in inventories are stored
as those numbers, so **only ever append** to the catalog. If you need a different
listing order, pin the numbering with `slotOrder`:

```kotlin
ItemCatalog(..., slotOrder = mapOf(ProviderKind.NATIVE to listOf("ruby_block", "ruby_pickaxe")))
```

Per-provider deviations (e.g. keeping the display name existing items were
created with) go in `CatalogItem.overrides`.

### Native fallback

With no external item plugin installed the manager returns a `NativeItemProvider`.
Items are vanilla `PAPER` with custom model data and an NBT tag; placed blocks are
note blocks whose instrument/note state selects the texture. `NativeSetup`
registers the catalog and builds the pack; serving it is up to your plugin:

```kotlin
val native = items.getProvider() as? NativeItemProvider ?: return
val nativeSetup = NativeSetup(catalog, native, dataFolder, logger, 1000, "MyPlugin-pack.zip", "MyPlugin items")
nativeSetup.registerItems()   // every startup, before item lookups

server.pluginManager.registerEvents(NativePlacementListener(this, native) { catalog.isBlock(it.namespacedId) }, this)
server.pluginManager.registerEvents(NativePackListener(native) { config.getBoolean("pack-required") }, this)

// After nativeSetup.install() has generated the zip:
val zip = nativeSetup.zipFile
native.packHash = nativeSetup.computePackHash(zip)
native.packUrl = "http://$host:$port/${zip.name}"
NativePackServer(logger).start(zip, port)
```

Native blocks need `block-updates.disable-noteblock-updates: true` in
`config/paper-global.yml`, otherwise Minecraft resets the note block state and
textures change at random.

## Adding it to your build

```kotlin
repositories {
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-public/") // Item-NBT-API
}

dependencies {
    implementation("com.github.badbabywentaway:Integrare:0.2.0")
}

tasks.shadowJar {
    // Item-NBT-API refuses to run unrelocated
    relocate("de.tr7zw.changeme.nbtapi", "com.example.myplugin.nbtapi")
    relocate("org.stephanosbad.integrare", "com.example.myplugin.integrare")
}
```

## Building

Requires JDK 25.

```
./gradlew build
```

## Status

- Targets Paper 26.3.
- The Nexo provider and `NexoSetup` are written against Nexo's public API and
  the config keys its parsers read, but have not been run against a live Nexo
  server (premium license required).

## License

LGPL v3 — see [LICENSE.txt](LICENSE.txt).
