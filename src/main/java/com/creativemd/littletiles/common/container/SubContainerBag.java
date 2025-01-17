package com.creativemd.littletiles.common.container;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.creativemd.creativecore.common.gui.controls.container.SlotControl;
import com.creativemd.creativecore.common.gui.event.container.SlotChangeEvent;
import com.creativemd.creativecore.common.gui.premade.SubContainerHeldItem;
import com.creativemd.creativecore.common.utils.mc.WorldUtils;
import com.creativemd.littletiles.common.gui.controls.SlotControlBlockIngredient;
import com.creativemd.littletiles.common.items.ItemBag;
import com.creativemd.littletiles.common.utils.grid.LittleGridContext;
import com.creativemd.littletiles.common.utils.ingredients.BlockIngredient;
import com.creativemd.littletiles.common.utils.ingredients.ColorUnit;
import com.creativemd.littletiles.common.utils.ingredients.IngredientUtils;
import com.creativemd.littletiles.common.utils.ingredients.Ingredients;
import com.n247s.api.eventapi.eventsystem.CustomEventSubscribe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.oredict.DyeUtils;

public class SubContainerBag extends SubContainerHeldItem {
	
	public SubContainerBag(EntityPlayer player, ItemStack stack, int index) {
		super(player, stack, index);
	}
	
	private static Field dyeColor = ReflectionHelper.findField(EnumDyeColor.class, "colorValue", "field_193351_w");
	
	@CustomEventSubscribe
	public void onSlotChange(SlotChangeEvent event) {
		if (event.source instanceof SlotControl) {
			if (event.source instanceof SlotControlBlockIngredient) {
				SlotControlBlockIngredient slot = (SlotControlBlockIngredient) event.source;
				
				if (slot.slot.getStack().isEmpty())
					slot.ingredient = null;
				else if (slot.ingredient != null)
					slot.ingredient.value = slot.slot.getStack().getCount() / (double) LittleGridContext.get().maxTilesPerBlock;
				
				List<BlockIngredient> inventory = new ArrayList<>();
				for (int y = 0; y < ItemBag.inventoryHeight; y++) {
					for (int x = 0; x < ItemBag.inventoryWidth; x++) {
						int index = x + y * ItemBag.inventoryWidth;
						BlockIngredient ingredient = ((SlotControlBlockIngredient) get("item" + index)).ingredient;
						if (ingredient != null)
							inventory.add(ingredient);
					}
				}
				
				ItemBag.saveInventory(stack, inventory);
				
				reloadControls();
			} else if (event.source.name.startsWith("input")) {
				
				ItemStack input = ((SlotControl) event.source).slot.getStack();
				
				Ingredients ingredients = IngredientUtils.getIngredientsOfStack(input);
				
				boolean containedColor = false;
				
				if (ingredients != null) {
					ColorUnit result = ItemBag.storeColor(stack, ingredients.color, true);
					if (result != null && result.equals(ingredients.color))
						return;
					
					containedColor = !ingredients.color.isEmpty();
					
					while (!input.isEmpty()) {
						if (ItemBag.storeBlocks(stack, ingredients.block.copy(), true) != null)
							break;
						input.shrink(1);
						ItemBag.storeBlocks(stack, ingredients.block.copy(), false);
						if (ItemBag.storeColor(stack, ingredients.color, false) != null)
							break;
					}
					
					updateSlots();
					
					player.playSound(SoundEvents.ENTITY_ITEMFRAME_PLACE, 1.0F, 1.0F);
				} else if (DyeUtils.isDye(input)) {
					try {
						Optional<EnumDyeColor> optional = DyeUtils.colorFromStack(input);
						if (!optional.isPresent())
							return;
						ColorUnit color = ColorUnit.getColors(dyeColor.getInt(optional.get()));
						
						color.scale(2);
						ColorUnit result = ItemBag.storeColor(stack, color, true);
						if (result != null && result.equals(color))
							return;
						while (!input.isEmpty()) {
							input.shrink(1);
							if (ItemBag.storeColor(stack, color, false) != null)
								break;
						}
						
						containedColor = true;
						
					} catch (IllegalArgumentException | IllegalAccessException e) {
						e.printStackTrace();
					}
					
				}
				
				if (containedColor) {
					reloadControls();
					
					player.playSound(SoundEvents.BLOCK_BREWING_STAND_BREW, 1.0F, 1.0F);
				}
			}
			
		}
	}
	
	public void reloadControls() {
		controls.clear();
		createControls();
		refreshControls();
		NBTTagCompound nbt = stack.getTagCompound().copy();
		nbt.setBoolean("reload", true);
		sendNBTToGui(nbt);
	}
	
	public InventoryBasic bagInventory;
	
	public void updateSlots() {
		List<BlockIngredient> inventory = ItemBag.loadInventory(stack);
		for (int y = 0; y < ItemBag.inventoryHeight; y++) {
			for (int x = 0; x < ItemBag.inventoryWidth; x++) {
				int index = x + y * ItemBag.inventoryWidth;
				bagInventory.setInventorySlotContents(index, index < inventory.size() ? inventory.get(index).getTileItemStack() : ItemStack.EMPTY);
				((SlotControlBlockIngredient) get("item" + index)).ingredient = index < inventory.size() ? inventory.get(index) : null;
			}
		}
	}
	
	@Override
	public void createControls() {
		
		List<BlockIngredient> inventory = ItemBag.loadInventory(stack);
		bagInventory = new InventoryBasic("item", false, ItemBag.inventorySize) {
			@Override
			public int getInventoryStackLimit() {
				return ItemBag.maxStackSizeOfTiles;
			}
		};
		for (int y = 0; y < ItemBag.inventoryHeight; y++) {
			for (int x = 0; x < ItemBag.inventoryWidth; x++) {
				int index = x + y * ItemBag.inventoryWidth;
				bagInventory.setInventorySlotContents(index, index < inventory.size() ? inventory.get(index).getTileItemStack() : ItemStack.EMPTY);
				controls.add(new SlotControlBlockIngredient(new Slot(bagInventory, index, 5 + x * 18, 5 + y * 18) {
					@Override
					public boolean isItemValid(ItemStack stack) {
						return false;
					}
				}, index < inventory.size() ? inventory.get(index) : null));
			}
		}
		
		InventoryBasic input = new InventoryBasic("input", false, 1);
		addSlotToContainer(new Slot(input, 0, 120, 5));
		
		addPlayerSlotsToContainer(player);
		
	}
	
	@Override
	public void onClosed() {
		ItemStack stack = ((SlotControl) get("input0")).slot.getStack();
		if (!stack.isEmpty())
			WorldUtils.dropItem(player, stack);
	}
	
	@Override
	public void onPacketReceive(NBTTagCompound nbt) {
		
	}
	
}
