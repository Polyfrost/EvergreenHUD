/*
 * Copyright (C) Evergreen [2020 - 2021]
 * This program comes with ABSOLUTELY NO WARRANTY
 * This is free software, and you are welcome to redistribute it
 * under the certain conditions that can be found here
 * https://www.gnu.org/licenses/lgpl-3.0.en.html
 */

package com.evergreenclient.hudmod.elements.impl;

import com.evergreenclient.hudmod.elements.Element;
import com.evergreenclient.hudmod.settings.impl.BooleanSetting;
import com.evergreenclient.hudmod.settings.impl.IntegerSetting;
import com.evergreenclient.hudmod.utils.element.ElementData;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

public class ElementBlockAbove extends Element {

    private int blockDistance = 0;

    public BooleanSetting notify;
    public IntegerSetting notifyHeight;

    @Override
    public void initialise() {
        addSettings(notify = new BooleanSetting("Notify", false));
        addSettings(notifyHeight = new IntegerSetting("Notify Height", 3, 1, 10, " blocks"));
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public ElementData getMetadata() {
        return new ElementData("Block Above", "Tells you if there is a block above your head. Useful for games like bedwars.");
    }

    @NotNull
    @Override
    protected String getValue() {
        return Integer.toString(blockDistance);
    }

    @SubscribeEvent
    public void livingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (mc.theWorld == null) return;

//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX + 1, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ + 1)).getBlock() != Blocks.air
//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX + 1, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ)).getBlock() != Blocks.air
//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX + 1, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ - 1)).getBlock() != Blocks.air
//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ + 1)).getBlock() != Blocks.air
//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ - 1)).getBlock() != Blocks.air
//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX - 1, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ - 1)).getBlock() != Blocks.air
//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX - 1, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ)).getBlock() != Blocks.air
//                || mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX - 1, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ + 1)).getBlock() != Blocks.air)

        boolean above = false;
        for (int i = 1; i < 10 + 1; i++) {
            Block b = mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ)).getBlock();
            if (b != Blocks.air && b != Blocks.water && b != Blocks.lava) {
                if (i <= notifyHeight.get() && (blockDistance > notifyHeight.get() || blockDistance == 0)) {
                    if (notify.get())
                        mc.thePlayer.playSound("random.orb", 0.1f, 0.5f);
                }
                blockDistance = i;
                above = true;
                break;
            }
        }
        if (!above)
            blockDistance = 0;
    }

    @Override
    public String getDisplayTitle() {
        return "Above";
    }
}
