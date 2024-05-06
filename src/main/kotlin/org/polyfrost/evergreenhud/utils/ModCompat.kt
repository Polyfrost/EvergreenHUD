package org.polyfrost.evergreenhud.utils

import org.polyfrost.evergreenhud.EvergreenHUD
import org.polyfrost.polynametag.config.ModConfig

var selfNameTagEnabled = false
    get() = EvergreenHUD.isPolyNametag && ModConfig.enabled && ModConfig.showOwnNametag