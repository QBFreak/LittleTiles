package com.creativemd.littletiles.common.items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.creativemd.creativecore.common.gui.container.SubContainer;
import com.creativemd.creativecore.common.gui.container.SubGui;
import com.creativemd.creativecore.common.gui.opener.GuiHandler;
import com.creativemd.creativecore.common.gui.opener.IGuiCreator;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.common.container.SubContainerBag;
import com.creativemd.littletiles.common.gui.SubGuiBag;
import com.creativemd.littletiles.common.utils.grid.LittleGridContext;
import com.creativemd.littletiles.common.utils.ingredients.BlockIngredient;
import com.creativemd.littletiles.common.utils.ingredients.BlockIngredient.BlockIngredients;
import com.creativemd.littletiles.common.utils.ingredients.ColorUnit;
import com.creativemd.littletiles.common.utils.ingredients.IngredientUtils;

import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemBag extends Item implements IGuiCreator {
	
	public static int colorUnitMaximum = 10000000;
	public static int inventoryWidth = 6;
	public static int inventoryHeight = 4;
	public static int inventorySize = inventoryWidth * inventoryHeight;
	public static int maxStackSize = 64;
	public static int maxStackSizeOfTiles = maxStackSize * LittleGridContext.get().maxTilesPerBlock;
	
	public ItemBag() {
		setCreativeTab(LittleTiles.littleTab);
		setMaxStackSize(1);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		
	}
	
	public static void saveInventory(ItemStack stack, List<BlockIngredient> inventory) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		NBTTagList list = new NBTTagList();
		int i = 0;
		for (BlockIngredient ingredient : inventory) {
			if (ingredient.block instanceof BlockAir && ingredient.value < LittleGridContext.getMax().minimumTileSize)
				continue;
			if (i >= inventorySize)
				break;
			list.appendTag(ingredient.writeToNBT(new NBTTagCompound()));
			i++;
		}
		
		stack.getTagCompound().setTag("inv", list);
	}
	
	public static List<BlockIngredient> loadInventory(ItemStack stack) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		List<BlockIngredient> inventory = new ArrayList<>();
		
		NBTTagList list = stack.getTagCompound().getTagList("inv", 10);
		int size = Math.min(inventorySize, list.tagCount());
		for (int i = 0; i < size; i++) {
			NBTTagCompound nbt = list.getCompoundTagAt(i);
			BlockIngredient ingredient = IngredientUtils.getBlockIngredient(nbt);
			if (ingredient != null && ingredient.value >= LittleGridContext.getMax().minimumTileSize)
				inventory.add(ingredient);
		}
		return inventory;
	}
	
	public static BlockIngredients drainBlocks(ItemStack stack, BlockIngredients ingredients, boolean simulate) {
		List<BlockIngredient> inventory = loadInventory(stack);
		for (Iterator<BlockIngredient> iterator = ingredients.getIngredients().iterator(); iterator.hasNext();) {
			BlockIngredient ingredient = iterator.next();
			
			for (Iterator iterator2 = inventory.iterator(); iterator2.hasNext();) {
				BlockIngredient invIngredient = (BlockIngredient) iterator2.next();
				if (invIngredient.equals(ingredient)) {
					double amount = Math.min(invIngredient.value, ingredient.value);
					ingredient.value -= amount;
					invIngredient.value -= amount;
					if (ingredient.value <= 0) {
						iterator.remove();
						break;
					}
					if (invIngredient.value <= 0)
						iterator2.remove();
				}
			}
		}
		
		if (!simulate)
			saveInventory(stack, inventory);
		
		if (ingredients.isEmpty())
			return null;
		return ingredients;
	}
	
	public static BlockIngredients storeBlocks(ItemStack stack, BlockIngredients ingredients, boolean simulate) {
		ingredients = ItemBag.storeBlocks(stack, ingredients, true, simulate);
		if (ingredients != null)
			ingredients = ItemBag.storeBlocks(stack, ingredients, false, simulate);
		return ingredients;
	}
	
	public static BlockIngredients storeBlocks(ItemStack stack, BlockIngredients ingredients, boolean stackOnly, boolean simulate) {
		List<BlockIngredient> inventory = loadInventory(stack);
		
		if (stackOnly) {
			for (Iterator<BlockIngredient> iterator = ingredients.getIngredients().iterator(); iterator.hasNext();) {
				BlockIngredient ingredient = iterator.next();
				for (BlockIngredient equal : inventory) {
					if (equal.equals(ingredient)) {
						double amount = Math.min(equal.value + ingredient.value, maxStackSize);
						ingredient.value -= amount - equal.value;
						equal.value = amount;
						if (ingredient.value <= 0) {
							iterator.remove();
							break;
						}
					}
				}
			}
		} else {
			for (Iterator<BlockIngredient> iterator = ingredients.getIngredients().iterator(); iterator.hasNext();) {
				BlockIngredient ingredient = iterator.next();
				if (inventory.size() >= inventorySize)
					break;
				
				double amount = Math.min(ingredient.value, maxStackSize);
				ingredient.value -= amount;
				
				if (ingredient.value <= 0)
					iterator.remove();
				
				inventory.add(ingredient.copy(amount));
			}
		}
		
		if (!simulate)
			saveInventory(stack, inventory);
		
		if (ingredients.isEmpty())
			return null;
		return ingredients;
	}
	
	public static ColorUnit loadColorUnit(ItemStack stack) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		return new ColorUnit(stack.getTagCompound().getInteger("black"), stack.getTagCompound().getInteger("cyan"), stack.getTagCompound().getInteger("magenta"), stack.getTagCompound().getInteger("yellow"));
	}
	
	public static void saveColorUnit(ItemStack stack, ColorUnit unit) {
		stack.getTagCompound().setInteger("black", unit.BLACK);
		stack.getTagCompound().setInteger("cyan", unit.CYAN);
		stack.getTagCompound().setInteger("magenta", unit.MAGENTA);
		stack.getTagCompound().setInteger("yellow", unit.YELLOW);
	}
	
	public static ColorUnit storeColor(ItemStack stack, ColorUnit unit, boolean simulate) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		ColorUnit result = unit.copy();
		
		int maxBlack = Math.min(stack.getTagCompound().getInteger("black") + unit.BLACK, colorUnitMaximum);
		if (stack.getTagCompound().getInteger("black") != maxBlack)
			result.BLACK -= maxBlack - stack.getTagCompound().getInteger("black");
		
		int maxRed = Math.min(stack.getTagCompound().getInteger("cyan") + unit.CYAN, colorUnitMaximum);
		if (stack.getTagCompound().getInteger("cyan") != maxRed)
			result.CYAN -= maxRed - stack.getTagCompound().getInteger("cyan");
		
		int maxGreen = Math.min(stack.getTagCompound().getInteger("magenta") + unit.MAGENTA, colorUnitMaximum);
		if (stack.getTagCompound().getInteger("magenta") != maxGreen)
			result.MAGENTA -= maxGreen - stack.getTagCompound().getInteger("magenta");
		
		int maxBlue = Math.min(stack.getTagCompound().getInteger("yellow") + unit.YELLOW, colorUnitMaximum);
		if (stack.getTagCompound().getInteger("yellow") != maxBlue)
			result.YELLOW -= maxBlue - stack.getTagCompound().getInteger("yellow");
		
		if (!simulate) {
			stack.getTagCompound().setInteger("black", maxBlack);
			stack.getTagCompound().setInteger("cyan", maxRed);
			stack.getTagCompound().setInteger("magenta", maxGreen);
			stack.getTagCompound().setInteger("yellow", maxBlue);
		}
		
		if (result.isEmpty())
			return null;
		
		return result;
	}
	
	public static ColorUnit drainColor(ItemStack stack, ColorUnit unit, boolean simulate) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		ColorUnit result = unit.copy();
		
		int drainBlack = Math.min(unit.BLACK, stack.getTagCompound().getInteger("black"));
		result.BLACK -= drainBlack;
		
		int drainRed = Math.min(unit.CYAN, stack.getTagCompound().getInteger("cyan"));
		result.CYAN -= drainRed;
		
		int drainGreen = Math.min(unit.MAGENTA, stack.getTagCompound().getInteger("magenta"));
		result.MAGENTA -= drainGreen;
		
		int drainBlue = Math.min(unit.YELLOW, stack.getTagCompound().getInteger("yellow"));
		result.YELLOW -= drainBlue;
		
		if (!simulate) {
			stack.getTagCompound().setInteger("black", stack.getTagCompound().getInteger("black") - drainBlack);
			stack.getTagCompound().setInteger("cyan", stack.getTagCompound().getInteger("cyan") - drainRed);
			stack.getTagCompound().setInteger("magenta", stack.getTagCompound().getInteger("magenta") - drainGreen);
			stack.getTagCompound().setInteger("yellow", stack.getTagCompound().getInteger("yellow") - drainBlue);
		}
		
		if (result.isEmpty())
			return null;
		return result;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public SubGui getGui(EntityPlayer player, ItemStack stack, World world, BlockPos pos, IBlockState state) {
		return new SubGuiBag(stack);
	}
	
	@Override
	public SubContainer getContainer(EntityPlayer player, ItemStack stack, World world, BlockPos pos, IBlockState state) {
		return new SubContainerBag(player, stack, player.inventory.currentItem);
	}
	
	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
		if (!world.isRemote)
			GuiHandler.openGuiItem(player, world);
		return new ActionResult(EnumActionResult.SUCCESS, player.getHeldItem(hand));
	}
	
}
