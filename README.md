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

### Native fallback

With no external item plugin installed the manager returns a `NativeItemProvider`.
Items are vanilla `PAPER` with custom model data and an NBT tag; placed blocks are
note blocks whose instrument/note state selects the texture. Your plugin is
responsible for registering its items and for generating the resource pack:

```kotlin
val native = items.getProvider() as? NativeItemProvider ?: return
native.registerItem("myplugin:ruby_block", 1000, Component.text("Ruby Block"))
native.registerBlockState("myplugin:ruby_block", Instrument.PIANO, 0)
native.loadPlacedBlocks(File(dataFolder, "placed-blocks.txt"))

server.pluginManager.registerEvents(NativePlacementListener(this, native), this)
server.pluginManager.registerEvents(NativePackListener(native) { config.getBoolean("pack-required") }, this)

// Once you have a pack zip:
native.packHash = sha1(zipFile)
native.packUrl = "http://$host:$port/${zipFile.name}"
NativePackServer(logger).start(zipFile, port)
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
    implementation("com.github.badbabywentaway:Integrare:0.1.0")
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
- The Nexo provider is written against Nexo's public API but has not been run
  against a live Nexo server (premium license required).

## License

LGPL v3 — see [LICENSE.txt](LICENSE.txt).
