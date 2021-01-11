/*
 * Copyright (C) Evergreen [2020 - 2021]
 * This program comes with ABSOLUTELY NO WARRANTY
 * This is free software, and you are welcome to redistribute it
 * under the certain conditions that can be found here
 * https://www.gnu.org/licenses/lgpl-3.0.en.html
 */

package com.evergreenclient.hudmod.elements;

import com.evergreenclient.hudmod.config.ElementConfig;
import com.evergreenclient.hudmod.settings.Setting;
import com.evergreenclient.hudmod.utils.Alignment;
import com.evergreenclient.hudmod.utils.Position;
import com.evergreenclient.hudmod.utils.element.ElementData;
import com.evergreenclient.hudmod.utils.gui.Hitbox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Element extends Gui {

    /* For child classes */
    protected static final Minecraft mc = Minecraft.getMinecraft();

    /* Config */
    private boolean enabled = false;
    private Position pos = Position.getPositionWithRawPositioning(10, 10, 1, new ScaledResolution(mc));
    private boolean title = true;
    private boolean brackets = false;
    private boolean shadow = true;
    private boolean inverted = false;
    private Alignment alignment = Alignment.RIGHT;
    private final List<Setting> customSettings = new ArrayList<>();

    /* Color */
    private Color textColor = new Color(255, 255, 255, 255);
    private Color bgColor = new Color(0, 0, 0, 100);

    private final Logger logger;
    private final ElementConfig config;

    public Element() {
        this.logger = LogManager.getLogger(getMetadata().getName());
        config = new ElementConfig(this);
        initialise();
        config.load();
    }

    public abstract void initialise();

    public abstract ElementData getMetadata();

    protected abstract String getValue();

    public abstract String getDisplayTitle();

    public String getDisplayString() {
        String builder = "";
        if (showBrackets())
            builder += "[";
        if (showTitle() && !isInverted())
            builder += getDisplayTitle() + ": ";
        builder += getValue();
        if (showTitle() && isInverted())
            builder += " " + getDisplayTitle();
        if (showBrackets())
            builder += "]";
        return builder;
    }

    /**
     * This can be overwritten if element has a very specific way of displaying itself
     */
    public void render() {
        GlStateManager.pushMatrix();
        GlStateManager.scale(getPosition().getScale(), getPosition().getScale(), 0);
        Hitbox hitbox = getHitbox();
        int x = getPosition().getRawX(new ScaledResolution(mc));
        int y = getPosition().getRawY(new ScaledResolution(mc));
        drawRect(hitbox.x, hitbox.y, hitbox.x + (hitbox.width), hitbox.y + (hitbox.height), getBgColor().getRGB());
        switch (getAlignment()) {
            case LEFT:
                mc.fontRendererObj.drawString(getDisplayString(), (x - mc.fontRendererObj.getStringWidth(getDisplayString())) / getPosition().getScale(), y / getPosition().getScale(), getTextColor().getRGB(), renderShadow());
                break;
            case CENTER:
                drawCenteredString(mc.fontRendererObj, getDisplayString(), x / getPosition().getScale(), y / getPosition().getScale(), getTextColor().getRGB(), renderShadow());
                break;
            case RIGHT:
                mc.fontRendererObj.drawString(getDisplayString(), x / getPosition().getScale(), y / getPosition().getScale(), getTextColor().getRGB(), renderShadow());
                break;
        }
        GlStateManager.popMatrix();
    }

    public void drawCenteredString(FontRenderer fontRendererIn, String text, float x, float y, int color, boolean shadow) {
        fontRendererIn.drawString(text, x - fontRendererIn.getStringWidth(text) / 2f, y, color, shadow);
    }

    public Hitbox getHitbox() {
        Hitbox hitbox = null;
        int width = mc.fontRendererObj.getStringWidth(getDisplayString());
        int x = getPosition().getRawX(new ScaledResolution(mc));
        int y = getPosition().getRawY(new ScaledResolution(mc));
        switch (getAlignment()) {
            case LEFT:
                hitbox = new Hitbox(x - width - 4, y - 4, width + 8, mc.fontRendererObj.FONT_HEIGHT + 8);
                break;
            case CENTER:
                hitbox = new Hitbox(x - (width / 2) - 4, y - 4, width + 8, mc.fontRendererObj.FONT_HEIGHT + 8);
                break;
            case RIGHT:
                hitbox = new Hitbox(x - 4, y - 4, width + 8, mc.fontRendererObj.FONT_HEIGHT + 8);
                break;
        }
        return hitbox;
    }

    public void resetSettings() {
        enabled = true;
        pos = Position.getPositionWithRawPositioning(10, 10, 1, new ScaledResolution(mc));
        title = true;
        brackets = false;
        inverted = false;
        shadow = true;
        alignment = Alignment.RIGHT;
        textColor = new Color(255, 255, 255, 255);
        bgColor = new Color(0, 0, 0, 100);
        customSettings.clear();
        getConfig().save();
    }

    public ElementConfig getConfig() {
        return config;
    }

    protected void addSettings(Setting... settings) {
        customSettings.addAll(Arrays.asList(settings));
    }

    public List<Setting> getCustomSettings() {
        return customSettings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Position getPosition() {
        return pos;
    }

    public boolean showTitle() {
        return title;
    }

    public boolean showBrackets() {
        return brackets;
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    public Color getTextColor() {
        return textColor;
    }

    public Color getBgColor() {
        return bgColor;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setBgColor(Color bgColor) {
        this.bgColor = bgColor;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
    }

    public boolean renderShadow() {
        return shadow;
    }

    public void setTitle(boolean title) {
        this.title = title;
    }

    public void setBrackets(boolean brackets) {
        this.brackets = brackets;
    }

    public void setTextColor(Color color) {
        this.textColor = color;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

}