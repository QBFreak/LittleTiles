package com.creativemd.littletiles.common.gui;

import java.util.ArrayList;

import com.creativemd.creativecore.common.gui.container.SubGui;
import com.creativemd.creativecore.common.gui.controls.gui.GuiButton;
import com.creativemd.creativecore.common.gui.controls.gui.GuiLabel;
import com.creativemd.creativecore.common.gui.controls.gui.custom.GuiItemListBox;
import com.creativemd.creativecore.common.utils.mc.ColorUtils;
import com.creativemd.littletiles.common.action.LittleAction;
import com.creativemd.littletiles.common.action.block.NotEnoughIngredientsException;
import com.creativemd.littletiles.common.container.SubContainerWorkbench;
import com.creativemd.littletiles.common.items.ItemRecipe;
import com.creativemd.littletiles.common.items.ItemRecipeAdvanced;
import com.creativemd.littletiles.common.tiles.preview.LittlePreviews;
import com.creativemd.littletiles.common.tiles.preview.LittleTilePreview;
import com.creativemd.littletiles.common.utils.ingredients.BlockIngredient;
import com.creativemd.littletiles.common.utils.ingredients.ColorUnit;
import com.creativemd.littletiles.common.utils.ingredients.IngredientUtils;
import com.creativemd.littletiles.common.utils.ingredients.Ingredients;
import com.creativemd.littletiles.common.utils.ingredients.StackIngredient;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class SubGuiWorkbench extends SubGui {
	
	public SubGuiWorkbench() {
		super(200, 200);
	}
	
	@Override
	public void createControls() {
		controls.add(new GuiLabel("->", 25, 6));
		controls.add(new GuiButton("Craft", 70, 3, 40) {
			
			@Override
			public void onClicked(int x, int y, int button) {
				ItemStack stack1 = ((SubContainerWorkbench) container).basic.getStackInSlot(0);
				ItemStack stack2 = ((SubContainerWorkbench) container).basic.getStackInSlot(1);
				
				GuiItemListBox listBox = (GuiItemListBox) get("missing");
				GuiLabel label = (GuiLabel) get("label");
				label.caption = "";
				listBox.clear();
				
				if (!stack1.isEmpty()) {
					if (stack1.getItem() instanceof ItemRecipe || stack1.getItem() instanceof ItemRecipeAdvanced) {
						LittlePreviews previews = LittleTilePreview.getPreview(stack1);
						
						EntityPlayer player = getPlayer();
						
						Ingredients ingredients = IngredientUtils.getIngredients(previews);
						
						try {
							if (LittleAction.drain(player, ingredients)) {
								sendPacketToServer(new NBTTagCompound());
							}
						} catch (NotEnoughIngredientsException e2) {
							Ingredients missing = LittleAction.getMissing(player, IngredientUtils.getIngredients(previews));
							
							if (ingredients.block != null)
								for (BlockIngredient ingredient : ingredients.block.getIngredients()) {
									int fullBlocks = (int) ingredient.value;
									int pixels = (int) Math.ceil(((ingredient.value - fullBlocks) * previews.context.maxTilesPerBlock));
									String line = fullBlocks > 0 ? fullBlocks + " blocks" : "";
									line += (fullBlocks > 0 ? " " : "") + pixels + " pixels";
									listBox.add(line, ingredient.getItemStack());
								}
							
							if (ingredients.color != null) {
								ColorUnit unit = ingredients.color;
								if (unit.BLACK > 0)
									listBox.add(unit.getBlackDescription(), ItemStack.EMPTY);
								if (unit.CYAN > 0)
									listBox.add(unit.getCyanDescription(), ItemStack.EMPTY);
								if (unit.MAGENTA > 0)
									listBox.add(unit.getMagentaDescription(), ItemStack.EMPTY);
								if (unit.YELLOW > 0)
									listBox.add(unit.getYellowDescription(), ItemStack.EMPTY);
							}
							
							for (StackIngredient stack : ingredients.getStacks()) {
								listBox.add(stack.count + "", stack.stack);
							}
							
						}
						
					} else {
						sendPacketToServer(new NBTTagCompound());
					}
				}
				
			}
			
		});
		controls.add(new GuiItemListBox("missing", 5, 25, 180, 70, new ArrayList<ItemStack>(), new ArrayList<String>()));
		controls.add(new GuiLabel("label", "", 5, 102, ColorUtils.RGBAToInt(255, 50, 50, 255)));
	}
	
}
