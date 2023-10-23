package cc.polyfrost.evergreenhud.hud.customimages

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.CustomOption
import cc.polyfrost.oneconfig.config.core.ConfigUtils
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.config.elements.BasicOption
import cc.polyfrost.oneconfig.config.elements.OptionPage
import java.lang.reflect.Field

class CustomImages : Config(Mod("Custom Images", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/customimages.json", false) {
    @CustomOption
    private var huds: Array<CustomImageHud> = emptyArray()

    @Transient
    var hudHolders: MutableList<CustomImageHudHolder> = ArrayList()

    @Transient
    var planToRemove: CustomImageHudHolder? = null

    init {
        initialize()
    }

    override fun load() {
        super.load()

        hudHolders = huds.mapTo(ArrayList()) { hud ->
            CustomImageHudHolder(this, hud)
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
        val basicOption = ImagesOptionList(this)
        ConfigUtils.getSubCategory(page, "General", "").options.add(basicOption)
        return basicOption
    }
}