# EvergreenHUD

[![Modrinth downloads](https://img.shields.io/modrinth/dt/1yIQcc2b?style=flat&logo=modrinth&label=Modrinth&color=1bd96a)](https://modrinth.com/mod/evergreenhud)
[![CurseForge downloads](https://img.shields.io/curseforge/dt/460419?style=flat&logo=curseforge&label=CurseForge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/evergreenhud)

EvergreenHUD adds 38 HUD elements to the OneConfig HUD editor, such as FPS, CPS, keystrokes, armor, potion effects and coordinates. Two more appear when your setup supports them. You place, scale and style each element yourself.

## Features

### Combat and input

- CPS shows your left clicks, right clicks or both per second.
- Keystrokes shows rows for the movement keys, spacebar and mouse buttons, with optional sprint and sneak rows. You choose the rows and their order, the key colors and the fade times. Custom mode draws one key of your choice.
- Mouse Strokes draws your mouse movement as a moving dot or as a trail.
- Combo counts your hits in a row on one target. It resets when you take damage or after 2 seconds without a hit.
- Reach shows the distance of your last hit.
- Block Place Count shows how many blocks you placed in the last second.

### Player and items

- Armor Status shows your armor, main hand and, on the Fabric versions, off hand. It can show durability, durability percent, item name or item count next to each item.
- Potion Effects lists your active effects with icon, name, amplifier and duration. You can sort and filter the list, and override the look of single effects.
- Inventory shows your inventory or your ender chest. On the Fabric versions it can also show the shulker box you hold, and a keybind pins that preview.
- Item Counter counts one item you pick, with options to include shulker boxes and your ender chest.
- Item Tracker lists the items you gain and lose. Each entry stays for 5 seconds by default.
- Item Lore shows the name and lore of the item in your main hand.
- Saturation shows your saturation value.
- Player Preview draws your player model, and Player Head draws the head of your skin.

### Position and world

- Position shows X, Y and Z, with optional yaw and pitch.
- Facing shows the direction you face as text, with optional bearing and axis.
- Direction draws a compass strip that scrolls as you turn.
- Speed shows your speed in meters per tick, meters per second, km/h or mph.
- Biome shows the biome you stand in.
- Block Above shows how many blocks of free space are above your head. It checks 10 blocks up by default and can play a sound when a block gets close.
- Day shows the world's day count, and In Game Time shows the world's time of day.
- Entity Counter shows how many entities the game renders, and the total.

### Performance and connection

- FPS takes a format string with the current, average, median, 95th and 99th percentile values.
- Ping shows your current ping, with the average and highest for the server in the format string.
- TPS estimates the server's ticks per second from the packets it sends.
- Memory shows the memory the game uses in GB or as a percentage.
- Server Address shows the address of the server you are on.
- Play Time shows how long the game has been open.
- Resource Pack shows the icon, name and description of your active resource pack.

### Clocks

- Clock draws an analog clock with your computer's time.
- Digital Clock shows the same time as text, in 24-hour or 12-hour format.

### Custom elements

- Custom Text shows any text you type, on one or more lines.
- Custom Image shows a PNG, JPG, BMP or WebP file from your computer.
- Shape draws a rectangle, ellipse, triangle, polygon, star or ring.

### Elements that need something else

- Battery shows your laptop's charge. It only exists on Windows, on a computer that has a battery.
- Shaders shows the name of your active shader pack. It only exists with Iris installed.
- Weather and Social Media Stats need an internet connection.

### Shared style

The EvergreenHUD settings page sets the font, text color, text scale, background, corner radius, shadow and padding for all elements at once. It applies each change to every OneConfig HUD element, including elements from other mods.

## Compatibility

- With [VanillaHUD](https://modrinth.com/mod/vanillahud) installed, the Potion Effects element has an option that hides VanillaHUD's status effects element, so the two do not overlap.

## Supported versions

- Minecraft 1.8.9 on Ornithe.
- Minecraft 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3 on Fabric.

EvergreenHUD needs [OneConfig](https://modrinth.com/mod/oneconfig) and Fabric Language Kotlin. The Fabric builds also need Fabric API.

## Support

- Report bugs and ask questions on the [Polyfrost Discord](https://polyfrost.org/discord).
- Check [Polyfrost Status](https://status.polyfrost.org/) if a Polyfrost service is down.

## Building

```sh
./gradlew ":26.3:build"
```

Replace `26.3` with any supported Minecraft version. The build needs JDK 21 and JDK 25 installed.
