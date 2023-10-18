package cc.polyfrost.evergreenhud.hud.customtexts

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.CustomOption
import cc.polyfrost.oneconfig.config.core.ConfigUtils
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.config.elements.BasicOption
import cc.polyfrost.oneconfig.config.elements.OptionPage
import java.lang.reflect.Field

class CustomTexts : Config(Mod("Custom Texts", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/customtexts.json", false) {
    @CustomOption
    private var huds: Array<CustomTextHud> = emptyArray()

    @Transient
    var hudHolders: MutableList<CustomTextHudHolder> = ArrayList()

    @Transient
    var planToRemove: CustomTextHudHolder? = null

    init {
        initialize()
    }

    override fun load() {
        super.load()

        hudHolders = huds.mapTo(ArrayList()) { hud ->
            CustomTextHudHolder(this, hud)
        }
    }

    override fun save() {
        huds = hudHolders.map { holder ->
            holder.hud
        }.toTypedArray()

        super.save()
    }

    override fun getCustomOption(
        field: Field,
        annotation: CustomOption,
        page: OptionPage,
        mod: Mod,
        migrate: Boolean
    ): BasicOption {
        val basicOption = TextHudsOptionList(this)
        ConfigUtils.getSubCategory(page, "General", "").options.add(basicOption)
        return basicOption
    }
}