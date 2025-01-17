package com.creativemd.littletiles.common.action;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.creativemd.creativecore.common.packet.CreativeCorePacket;
import com.creativemd.creativecore.common.packet.PacketHandler;
import com.creativemd.creativecore.common.utils.mc.ColorUtils;
import com.creativemd.creativecore.common.utils.mc.InventoryUtils;
import com.creativemd.creativecore.common.utils.mc.PlayerUtils;
import com.creativemd.creativecore.common.utils.mc.WorldUtils;
import com.creativemd.creativecore.common.world.CreativeWorld;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.LittleTilesConfig;
import com.creativemd.littletiles.common.action.block.NotEnoughIngredientsException;
import com.creativemd.littletiles.common.blocks.BlockTile;
import com.creativemd.littletiles.common.config.SpecialServerConfig;
import com.creativemd.littletiles.common.entity.EntityAnimation;
import com.creativemd.littletiles.common.items.ItemBag;
import com.creativemd.littletiles.common.items.ItemPremadeStructure;
import com.creativemd.littletiles.common.mods.chiselsandbits.ChiselsAndBitsManager;
import com.creativemd.littletiles.common.packet.LittleBlockUpdatePacket;
import com.creativemd.littletiles.common.packet.LittleBlocksUpdatePacket;
import com.creativemd.littletiles.common.structure.LittleStructure;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.tiles.LittleTile;
import com.creativemd.littletiles.common.tiles.LittleTileBlock;
import com.creativemd.littletiles.common.tiles.preview.LittleAbsolutePreviews;
import com.creativemd.littletiles.common.tiles.preview.LittleAbsolutePreviewsStructure;
import com.creativemd.littletiles.common.tiles.preview.LittlePreviews;
import com.creativemd.littletiles.common.tiles.preview.LittlePreviewsStructure;
import com.creativemd.littletiles.common.tiles.preview.LittleTilePreview;
import com.creativemd.littletiles.common.tiles.vec.LittleBoxes;
import com.creativemd.littletiles.common.tiles.vec.LittleTileBox;
import com.creativemd.littletiles.common.tiles.vec.LittleTileIdentifierAbsolute;
import com.creativemd.littletiles.common.tiles.vec.LittleTilePos;
import com.creativemd.littletiles.common.tiles.vec.LittleTileVec;
import com.creativemd.littletiles.common.tiles.vec.LittleTileVecContext;
import com.creativemd.littletiles.common.utils.compression.LittleNBTCompressionTools;
import com.creativemd.littletiles.common.utils.grid.LittleGridContext;
import com.creativemd.littletiles.common.utils.ingredients.BlockIngredient;
import com.creativemd.littletiles.common.utils.ingredients.BlockIngredient.BlockIngredients;
import com.creativemd.littletiles.common.utils.ingredients.ColorUnit;
import com.creativemd.littletiles.common.utils.ingredients.IngredientUtils;
import com.creativemd.littletiles.common.utils.ingredients.Ingredients;
import com.creativemd.littletiles.common.utils.ingredients.StackIngredient;
import com.creativemd.littletiles.common.utils.placing.PlacementMode;
import com.creativemd.littletiles.common.utils.selection.selector.TileSelector;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class LittleAction extends CreativeCorePacket {
	
	private static List<LittleAction> lastActions = new ArrayList<>();
	
	private static int index = 0;
	
	@SideOnly(Side.CLIENT)
	public static boolean isUsingSecondMode(EntityPlayer player) {
		if (LittleTilesConfig.building.useALTForEverything)
			return GuiScreen.isAltKeyDown();
		if (LittleTilesConfig.building.useAltWhenFlying)
			return player.capabilities.isFlying ? GuiScreen.isAltKeyDown() : player.isSneaking();
		return player.isSneaking();
	}
	
	public static void rememberAction(LittleAction action) {
		if (!action.canBeReverted())
			return;
		
		if (index > 0) {
			if (index < lastActions.size())
				lastActions = lastActions.subList(index, lastActions.size() - 1);
			else
				lastActions = new ArrayList<>();
		}
		
		index = 0;
		
		if (lastActions.size() == LittleTilesConfig.building.maxSavedActions)
			lastActions.remove(LittleTilesConfig.building.maxSavedActions - 1);
		
		lastActions.add(0, action);
	}
	
	@SideOnly(Side.CLIENT)
	public static boolean undo() throws LittleActionException {
		if (lastActions.size() > index) {
			EntityPlayer player = Minecraft.getMinecraft().player;
			
			LittleAction reverted = lastActions.get(index).revert();
			
			if (reverted == null)
				throw new LittleActionException("action.revert.notavailable");
			
			if (reverted.action(player)) {
				PacketHandler.sendPacketToServer(reverted);
				lastActions.set(index, reverted);
				index++;
				return true;
			}
		}
		return false;
	}
	
	@SideOnly(Side.CLIENT)
	public static boolean redo() throws LittleActionException {
		if (index > 0 && index <= lastActions.size()) {
			EntityPlayer player = Minecraft.getMinecraft().player;
			
			index--;
			
			LittleAction reverted = lastActions.get(index).revert();
			
			if (reverted == null)
				throw new LittleActionException("action.revert.notavailable");
			
			if (reverted.action(player)) {
				PacketHandler.sendPacketToServer(reverted);
				lastActions.set(index, reverted);
				
				return true;
			}
		}
		return false;
	}
	
	public static void registerLittleAction(String id, Class<? extends LittleAction>... classTypes) {
		for (int i = 0; i < classTypes.length; i++) {
			CreativeCorePacket.registerPacket(classTypes[i], "ac" + id + i);
		}
	}
	
	/** Must be implemented by every action **/
	public LittleAction() {
		
	}
	
	@SideOnly(Side.CLIENT)
	public abstract boolean canBeReverted();
	
	/** @return null if an revert action is not available */
	@SideOnly(Side.CLIENT)
	public abstract LittleAction revert() throws LittleActionException;
	
	public boolean sendToServer() {
		return true;
	}
	
	protected abstract boolean action(EntityPlayer player) throws LittleActionException;
	
	@SideOnly(Side.CLIENT)
	public boolean execute() {
		EntityPlayer player = Minecraft.getMinecraft().player;
		
		try {
			if (action(player)) {
				rememberAction(this);
				
				if (sendToServer())
					PacketHandler.sendPacketToServer(this);
				return true;
			}
		} catch (LittleActionException e) {
			player.sendStatusMessage(new TextComponentString(e.getLocalizedMessage()), true);
			return false;
		}
		
		return false;
	}
	
	@Override
	public void executeClient(EntityPlayer player) {
		// Not used yet
		
		try {
			action(player);
		} catch (LittleActionException e) {
			player.sendStatusMessage(new TextComponentString(e.getLocalizedMessage()), true);
		}
	}
	
	@Override
	public void executeServer(EntityPlayer player) {
		try {
			action(player);
		} catch (LittleActionException e) {
			player.sendStatusMessage(new TextComponentString(e.getLocalizedMessage()), true);
		}
	}
	
	public static boolean canConvertBlock(EntityPlayer player, World world, BlockPos pos, IBlockState state) {
		if (player.isCreative())
			return true;
		if (SpecialServerConfig.strictMining)
			return false;
		if (!SpecialServerConfig.editUnbreakable)
			return state.getBlock().getBlockHardness(state, world, pos) > 0;
		return SpecialServerConfig.canEditBlock(player, state, pos);
	}
	
	public static boolean canUseUndoOrRedo(EntityPlayer player) {
		GameType type = PlayerUtils.getGameType(player);
		return type == GameType.CREATIVE || type == GameType.SURVIVAL;
	}
	
	public static boolean canPlace(EntityPlayer player) {
		GameType type = PlayerUtils.getGameType(player);
		if (type == GameType.CREATIVE || type == GameType.SURVIVAL || type == GameType.ADVENTURE)
			return true;
		return false;
	}
	
	public static TileEntityLittleTiles loadTe(EntityPlayer player, World world, BlockPos pos, boolean shouldConvert) {
		TileEntity tileEntity = world.getTileEntity(pos);
		
		if (!(tileEntity instanceof TileEntityLittleTiles)) {
			List<LittleTile> tiles = ChiselsAndBitsManager.getTiles(tileEntity);
			LittleGridContext context = tiles != null ? LittleGridContext.get(ChiselsAndBitsManager.convertingFrom) : LittleGridContext.get();
			if (tileEntity == null && tiles == null) {
				IBlockState state = world.getBlockState(pos);
				if (shouldConvert && isBlockValid(state.getBlock()) && canConvertBlock(player, world, pos, state)) {
					tiles = new ArrayList<>();
					
					context = LittleGridContext.get(LittleGridContext.minSize);
					
					LittleTileBox box = new LittleTileBox(context.minPos, context.minPos, context.minPos, context.maxPos, context.maxPos, context.maxPos);
					
					LittleTile tile = new LittleTileBlock(state.getBlock(), state.getBlock().getMetaFromState(state));
					tile.box = box;
					tiles.add(tile);
				} else if (state.getMaterial().isReplaceable()) {
					// new TileEntityLittleTiles();
					if (!world.setBlockState(pos, BlockTile.getState(false, false)))
						return null;
					tileEntity = world.getTileEntity(pos);
				}
			}
			
			if (tiles != null && tiles.size() > 0) {
				world.setBlockState(pos, BlockTile.getState(tiles));
				tileEntity = world.getTileEntity(pos);
				((TileEntityLittleTiles) tileEntity).convertTo(context);
				for (LittleTile tile : tiles) {
					tile.te = (TileEntityLittleTiles) tileEntity;
					tile.place();
				}
			}
		}
		
		if (tileEntity instanceof TileEntityLittleTiles)
			return (TileEntityLittleTiles) tileEntity;
		return null;
	}
	
	private static Method loadWorldEditEvent() {
		try {
			Class clazz = Class.forName("com.sk89q.worldedit.forge.ForgeWorldEdit");
			worldEditInstance = clazz.getField("inst").get(null);
			return clazz.getMethod("onPlayerInteract", PlayerInteractEvent.class);
		} catch (Exception e) {
			
		}
		return null;
	}
	
	private static Method WorldEditEvent = loadWorldEditEvent();
	private static Object worldEditInstance = null;
	
	public static void sendEntityResetToClient(EntityPlayerMP player, EntityAnimation animation) {
		//tobedone
	}
	
	public static void sendBlockResetToClient(World world, EntityPlayerMP player, BlockPos pos) {
		if (world instanceof CreativeWorld)
			PacketHandler.sendPacketToPlayer(new LittleBlockUpdatePacket(world, pos, null), player);
		else
			player.connection.sendPacket(new SPacketBlockChange(player.world, pos));
	}
	
	public static void sendBlockResetToClient(World world, EntityPlayerMP player, TileEntityLittleTiles te) {
		if (world instanceof CreativeWorld)
			PacketHandler.sendPacketToPlayer(new LittleBlockUpdatePacket(world, te.getPos(), te), player);
		else {
			player.connection.sendPacket(new SPacketBlockChange(player.world, te.getPos()));
			if (te != null)
				player.connection.sendPacket(te.getUpdatePacket());
		}
	}
	
	public static void sendBlockResetToClient(World world, EntityPlayerMP player, Set<TileEntityLittleTiles> tileEntities) {
		PacketHandler.sendPacketToPlayer(new LittleBlocksUpdatePacket(world, tileEntities), player);
	}
	
	public static void sendBlockResetToClient(World world, EntityPlayerMP player, LittleStructure structure) {
		sendBlockResetToClient(world, player, structure.blocks());
	}
	
	public static boolean isAllowedToInteract(EntityPlayer player, EntityAnimation animation, boolean rightClick) {
		if (player.isSpectator() || (!rightClick && (PlayerUtils.isAdventure(player) || !player.isAllowEdit())))
			return false;
		
		return true;
	}
	
	public static boolean isAllowedToInteract(World world, EntityPlayer player, BlockPos pos, boolean rightClick, EnumFacing facing) {
		if (player == null || player.world.isRemote)
			return true;
		
		if (player.isSpectator() || (!rightClick && (PlayerUtils.isAdventure(player) || !player.isAllowEdit())))
			return false;
		
		if (WorldEditEvent != null) {
			PlayerInteractEvent event = rightClick ? new PlayerInteractEvent.RightClickBlock(player, EnumHand.MAIN_HAND, pos, facing, new Vec3d(pos)) : new PlayerInteractEvent.LeftClickBlock(player, pos, facing, new Vec3d(pos));
			try {
				if (worldEditInstance == null)
					loadWorldEditEvent();
				WorldEditEvent.invoke(worldEditInstance, event);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
			if (event.isCanceled())
				return false;
		}
		
		return !player.getServer().isBlockProtected(player.world, pos, player);
	}
	
	public static boolean isAllowedToPlacePreview(EntityPlayer player, LittleTilePreview preview) throws LittleActionException {
		if (preview == null)
			return true;
		
		if (preview.hasColor() && ColorUtils.getAlpha(preview.getColor()) < SpecialServerConfig.getMinimumTransparency(player))
			throw new SpecialServerConfig.NotAllowedToPlaceColorException();
		
		return true;
	}
	
	public static boolean isTileStillInPlace(LittleTile tile) {
		return tile.te.getTiles().contains(tile);
	}
	
	public static LittleTile getTile(World world, LittleTileIdentifierAbsolute coord) throws LittleActionException {
		TileEntity te = world.getTileEntity(coord.pos);
		if (te instanceof TileEntityLittleTiles) {
			LittleTile tile = ((TileEntityLittleTiles) te).getTile(coord.context, coord.identifier);
			if (tile != null)
				return tile;
			throw new LittleActionException.TileNotFoundException();
		} else
			throw new LittleActionException.TileEntityNotFoundException();
	}
	
	public static void writeAbsoluteCoord(LittleTileIdentifierAbsolute coord, ByteBuf buf) {
		writePos(buf, coord.pos);
		buf.writeInt(coord.identifier.length);
		for (int i = 0; i < coord.identifier.length; i++) {
			buf.writeInt(coord.identifier[i]);
		}
		writeContext(coord.context, buf);
	}
	
	public static LittleTileIdentifierAbsolute readAbsoluteCoord(ByteBuf buf) {
		BlockPos pos = readPos(buf);
		int[] identifier = new int[buf.readInt()];
		for (int i = 0; i < identifier.length; i++) {
			identifier[i] = buf.readInt();
		}
		return new LittleTileIdentifierAbsolute(pos, readContext(buf), identifier);
	}
	
	public static void writePreviews(LittlePreviews previews, ByteBuf buf) {
		buf.writeBoolean(previews.isAbsolute());
		buf.writeBoolean(previews.hasStructure());
		if (previews.hasStructure())
			writeNBT(buf, previews.getStructureData());
		if (previews.isAbsolute())
			writePos(buf, ((LittleAbsolutePreviews) previews).pos);
		
		writeContext(previews.context, buf);
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setTag("list", LittleNBTCompressionTools.writePreviews(previews));
		
		NBTTagList children = new NBTTagList();
		for (LittlePreviews child : previews.getChildren()) {
			children.appendTag(LittleTilePreview.saveChildPreviews(child));
		}
		nbt.setTag("children", children);
		
		writeNBT(buf, nbt);
	}
	
	public static LittlePreviews readPreviews(ByteBuf buf) {
		boolean absolute = buf.readBoolean();
		boolean structure = buf.readBoolean();
		
		NBTTagCompound nbt;
		LittlePreviews previews;
		if (absolute) {
			if (structure)
				previews = LittleNBTCompressionTools.readPreviews(new LittleAbsolutePreviewsStructure(readNBT(buf), readPos(buf), readContext(buf)), (nbt = readNBT(buf)).getTagList("list", 10));
			else
				previews = LittleNBTCompressionTools.readPreviews(new LittleAbsolutePreviews(readPos(buf), readContext(buf)), (nbt = readNBT(buf)).getTagList("list", 10));
		} else {
			if (structure)
				previews = LittleNBTCompressionTools.readPreviews(new LittlePreviewsStructure(readNBT(buf), readContext(buf)), (nbt = readNBT(buf)).getTagList("list", 10));
			else
				previews = LittleNBTCompressionTools.readPreviews(new LittlePreviews(readContext(buf)), (nbt = readNBT(buf)).getTagList("list", 10));
		}
		
		NBTTagList list = nbt.getTagList("children", 10);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound child = list.getCompoundTagAt(i);
			previews.addChild(LittlePreviews.getChild(previews.context, child));
		}
		return previews;
	}
	
	public static void writePlacementMode(PlacementMode mode, ByteBuf buf) {
		writeString(buf, mode.name);
	}
	
	public static PlacementMode readPlacementMode(ByteBuf buf) {
		return PlacementMode.getModeOrDefault(readString(buf));
	}
	
	public static void writeContext(LittleGridContext context, ByteBuf buf) {
		buf.writeInt(context.size);
	}
	
	public static LittleGridContext readContext(ByteBuf buf) {
		return LittleGridContext.get(buf.readInt());
	}
	
	public static void writeLittleVecContext(LittleTileVecContext vec, ByteBuf buf) {
		writeContext(vec.context, buf);
		writeLittleVec(vec.vec, buf);
	}
	
	public static LittleTileVecContext readLittleVecContext(ByteBuf buf) {
		return new LittleTileVecContext(readContext(buf), readLittleVec(buf));
	}
	
	public static void writeBoxes(LittleBoxes boxes, ByteBuf buf) {
		writePos(buf, boxes.pos);
		writeContext(boxes.context, buf);
		buf.writeInt(boxes.size());
		for (LittleTileBox box : boxes) {
			writeLittleBox(box, buf);
		}
	}
	
	public static LittleBoxes readBoxes(ByteBuf buf) {
		BlockPos pos = readPos(buf);
		LittleGridContext context = readContext(buf);
		LittleBoxes boxes = new LittleBoxes(pos, context);
		int length = buf.readInt();
		for (int i = 0; i < length; i++) {
			boxes.add(readLittleBox(buf));
		}
		return boxes;
	}
	
	public static void writeLittlePos(LittleTilePos pos, ByteBuf buf) {
		writePos(buf, pos.pos);
		writeLittleVecContext(pos.contextVec, buf);
	}
	
	public static LittleTilePos readLittlePos(ByteBuf buf) {
		return new LittleTilePos(readPos(buf), readLittleVecContext(buf));
	}
	
	public static void writeLittleVec(LittleTileVec vec, ByteBuf buf) {
		buf.writeInt(vec.x);
		buf.writeInt(vec.y);
		buf.writeInt(vec.z);
	}
	
	public static LittleTileVec readLittleVec(ByteBuf buf) {
		return new LittleTileVec(buf.readInt(), buf.readInt(), buf.readInt());
	}
	
	public static void writeSelector(TileSelector selector, ByteBuf buf) {
		writeNBT(buf, selector.writeNBT(new NBTTagCompound()));
	}
	
	public static TileSelector readSelector(ByteBuf buf) {
		return TileSelector.loadSelector(readNBT(buf));
	}
	
	public static void writeLittleBox(LittleTileBox box, ByteBuf buf) {
		int[] array = box.getArray();
		buf.writeInt(array.length);
		for (int i = 0; i < array.length; i++) {
			buf.writeInt(array[i]);
		}
	}
	
	public static LittleTileBox readLittleBox(ByteBuf buf) {
		int[] array = new int[buf.readInt()];
		for (int i = 0; i < array.length; i++) {
			array[i] = buf.readInt();
		}
		return LittleTileBox.createBox(array);
	}
	
	public static boolean needIngredients(EntityPlayer player) {
		return !player.isCreative();
	}
	
	public static boolean canDrain(EntityPlayer player, LittlePreviews previews) throws NotEnoughIngredientsException {
		if (needIngredients(player))
			return canDrain(player, IngredientUtils.getIngredients(previews));
		return true;
	}
	
	public static boolean drain(EntityPlayer player, LittlePreviews previews) throws NotEnoughIngredientsException {
		if (needIngredients(player))
			return drain(player, IngredientUtils.getIngredients(previews));
		return true;
	}
	
	public static Ingredients getMissing(EntityPlayer player, Ingredients ingredients) {
		if (needIngredients(player)) {
			Ingredients missing = new Ingredients();
			List<ItemStack> bags = getBags(player);
			List<ItemStack> usedBags = new ArrayList<>(); // Those bags will be drained in order to simulate the action.
			BlockIngredients toCheck = ingredients.block != null ? ingredients.block.copy() : null; // Temporary
			ColorUnit color = ingredients.color != null ? ingredients.color.copy() : null; // Temporary
			
			if (color != null && color.isEmpty())
				color = null;
			
			for (ItemStack stack : bags) {
				ItemStack used = stack.copy();
				
				if (toCheck != null)
					toCheck = ItemBag.drainBlocks(used, toCheck, false);
				if (color != null)
					color = ItemBag.drainColor(used, color, false);
				
				usedBags.add(used);
			}
			
			if (color != null)
				missing.color = color;
			
			if (toCheck != null || ingredients.hasStacks()) {
				List<ItemStack> inventory = InventoryUtils.copy(player.inventory);
				
				if (toCheck != null) {
					BlockIngredients additionalIngredients = new BlockIngredients();
					
					for (Iterator iterator = inventory.iterator(); iterator.hasNext();) {
						ItemStack itemStack = (ItemStack) iterator.next();
						BlockIngredient leftOver = toCheck.drainItemStack(itemStack);
						
						if (leftOver != null)
							additionalIngredients.addIngredient(leftOver);
						
						if (itemStack.isEmpty())
							iterator.remove();
						
						if (toCheck.isEmpty())
							break;
					}
					
					if (!toCheck.isEmpty())
						missing.block = toCheck;
					
				}
				
				if (ingredients.hasStacks())
					for (Iterator iterator = ingredients.getStacks().iterator(); iterator.hasNext();) {
						StackIngredient stackIngredient = (StackIngredient) iterator.next();
						if (stackIngredient.drain(inventory))
							iterator.remove();
						else
							ingredients.addStack(stackIngredient);
					}
			}
			
		}
		return ingredients;
	}
	
	public static boolean canDrain(EntityPlayer player, Ingredients ingredients) throws NotEnoughIngredientsException {
		if (needIngredients(player)) {
			List<ItemStack> bags = getBags(player);
			List<ItemStack> usedBags = new ArrayList<>(); // Those bags will be drained in order to simulate the action.
			BlockIngredients toCheck = ingredients.block != null ? ingredients.block.copy() : null; // Temporary
			ColorUnit color = ingredients.color != null ? ingredients.color.copy() : null; // Temporary
			
			if (color != null && color.isEmpty())
				color = null;
			
			for (ItemStack stack : bags) {
				ItemStack used = stack.copy();
				
				if (toCheck != null)
					toCheck = ItemBag.drainBlocks(used, toCheck, false);
				if (color != null)
					color = ItemBag.drainColor(used, color, false);
				
				usedBags.add(used);
			}
			
			if (color != null)
				throw new NotEnoughIngredientsException.NotEnoughColorException(color);
			
			if (toCheck != null || ingredients.hasStacks()) {
				List<ItemStack> inventory = InventoryUtils.copy(player.inventory);
				
				if (toCheck != null) {
					BlockIngredients additionalIngredients = new BlockIngredients();
					
					for (Iterator iterator = inventory.iterator(); iterator.hasNext();) {
						ItemStack itemStack = (ItemStack) iterator.next();
						BlockIngredient leftOver = toCheck.drainItemStack(itemStack);
						
						if (leftOver != null)
							additionalIngredients.addIngredient(leftOver);
						
						if (itemStack.isEmpty())
							iterator.remove();
						
						if (toCheck.isEmpty())
							break;
					}
					
					if (!toCheck.isEmpty())
						throw new NotEnoughIngredientsException.NotEnoughVolumeExcepion(toCheck);
					
					addIngredients(usedBags, additionalIngredients, null); // Check whether there is space for the additional ingredients (drain from ordinary itemstacks)
				}
				
				if (ingredients.hasStacks())
					for (Iterator iterator = ingredients.getStacks().iterator(); iterator.hasNext();) {
						StackIngredient stackIngredient = (StackIngredient) iterator.next();
						if (stackIngredient.drain(inventory))
							iterator.remove();
						else
							throw new NotEnoughIngredientsException.NotEnoughStackException(stackIngredient.stack);
					}
			}
			
		}
		return true;
	}
	
	public static boolean drain(EntityPlayer player, Ingredients ingredients) throws NotEnoughIngredientsException {
		if (needIngredients(player) && canDrain(player, ingredients)) {
			List<ItemStack> bags = getBags(player);
			
			BlockIngredients toCheck = ingredients.block != null ? ingredients.block.copy() : null; // Temporary
			ColorUnit color = ingredients.color != null ? ingredients.color.copy() : null; // Temporary
			
			// enough ingredients and enough space (if it needs to drain additional itemstacks)
			for (ItemStack stack : bags) {
				if (toCheck != null)
					toCheck = ItemBag.drainBlocks(stack, toCheck, false);
				if (color != null)
					color = ItemBag.drainColor(stack, color, false);
			}
			
			if (toCheck != null || ingredients.hasStacks()) {
				List<ItemStack> inventory = InventoryUtils.asList(player.inventory);
				
				if (toCheck != null) {
					BlockIngredients additionalIngredients = new BlockIngredients();
					
					for (Iterator iterator = inventory.iterator(); iterator.hasNext();) {
						ItemStack itemStack = (ItemStack) iterator.next();
						BlockIngredient leftOver = toCheck.drainItemStack(itemStack);
						
						if (leftOver != null)
							additionalIngredients.addIngredient(leftOver);
						
						if (itemStack.isEmpty())
							iterator.remove();
						
						if (toCheck.isEmpty())
							break;
					}
					
					if (!toCheck.isEmpty())
						throw new NotEnoughIngredientsException.NotEnoughVolumeExcepion(toCheck);
					
					addIngredients(bags, additionalIngredients, null); // Check whether there is space for the
					                                                   // additional ingredients (drain from ordinary
					                                                   // itemstacks)
				}
				
				if (ingredients.hasStacks())
					for (Iterator iterator = ingredients.getStacks().iterator(); iterator.hasNext();) {
						StackIngredient stackIngredient = (StackIngredient) iterator.next();
						if (stackIngredient.drain(inventory))
							iterator.remove();
						else
							throw new NotEnoughIngredientsException.NotEnoughStackException(stackIngredient.stack);
					}
			}
			
		}
		return true;
	}
	
	public static void dropPreviews(EntityPlayer player, LittlePreviews previews) {
		for (LittleTilePreview preview : previews) {
			if (preview.hasBlockIngredient())
				WorldUtils.dropItem(player, preview.getBlockIngredient(previews.context).getTileItemStack());
		}
	}
	
	/** Cannot be used for anything else but ingredients calculations
	 * 
	 * @param tiles
	 * @return */
	public static LittlePreviews getIngredientsPreviews(List<LittleTile> tiles) {
		LittlePreviews previews = new LittlePreviews(tiles.get(0).getContext());
		previews.addTiles(tiles);
		return previews;
	}
	
	public static boolean addTilesToInventoryOrDrop(EntityPlayer player, List<LittleTile> tiles) {
		if (needIngredients(player) && !tiles.isEmpty()) {
			LittlePreviews previews = getIngredientsPreviews(tiles);
			try {
				return addPreviewToInventory(player, previews);
			} catch (NotEnoughIngredientsException e) {
				dropPreviews(player, previews);
			}
		}
		return true;
	}
	
	public static boolean addTileToInventory(EntityPlayer player, LittleTile tile) throws NotEnoughIngredientsException {
		LittlePreviews previews = new LittlePreviews(tile.getContext());
		previews.addTile(tile);
		return addPreviewToInventory(player, previews);
	}
	
	public static boolean addTilesToInventory(EntityPlayer player, List<LittleTile> tiles) throws NotEnoughIngredientsException {
		if (tiles.isEmpty())
			return true;
		
		if (needIngredients(player))
			return addPreviewToInventory(player, getIngredientsPreviews(tiles));
		
		return true;
	}
	
	public static boolean addPreviewToInventory(EntityPlayer player, LittlePreviews previews) throws NotEnoughIngredientsException {
		if (needIngredients(player)) {
			ColorUnit color = new ColorUnit();
			BlockIngredients ingredients = new BlockIngredients();
			for (LittleTilePreview preview : previews) {
				if (preview.canBeConvertedToBlockEntry()) {
					ingredients.addIngredient(preview.getBlockIngredient(previews.context));
					color.addColorUnit(ColorUnit.getColors(previews.context, preview));
				}
			}
			return addIngredients(player, ingredients, color);
		}
		return true;
	}
	
	public static boolean store(List<ItemStack> bags, BlockIngredients toCheck, ColorUnit color, boolean simulate) throws NotEnoughIngredientsException {
		if ((toCheck == null || toCheck.isEmpty()) && (color == null || color.isEmpty()))
			return true;
		
		for (ItemStack stack : bags) {
			if (toCheck != null)
				toCheck = ItemBag.storeBlocks(stack, toCheck, true, simulate);
			if (color != null)
				color = ItemBag.storeColor(stack, color, simulate);
			
			if (toCheck == null && color == null)
				break;
		}
		
		if (color == null && toCheck != null) {
			for (ItemStack stack : bags) {
				toCheck = ItemBag.storeBlocks(stack, toCheck, false, simulate);
				if (toCheck == null)
					break;
			}
		}
		
		if (color != null && !color.isEmpty())
			throw new NotEnoughIngredientsException.NotEnoughColorSpaceException();
		
		if (toCheck != null && !toCheck.isEmpty())
			throw new NotEnoughIngredientsException.NotEnoughVolumeSpaceException();
		
		return true;
	}
	
	public static boolean addIngredients(EntityPlayer player, Ingredients ingredients) throws NotEnoughIngredientsException {
		return addIngredients(player, ingredients.block, ingredients.color);
	}
	
	public static boolean addIngredients(EntityPlayer player, BlockIngredients ingredients, ColorUnit unit) throws NotEnoughIngredientsException {
		return addIngredients(player, ingredients, unit, false);
	}
	
	public static boolean addIngredients(EntityPlayer player, BlockIngredients ingredients, ColorUnit unit, boolean simulate) throws NotEnoughIngredientsException {
		if (needIngredients(player)) {
			List<ItemStack> bags = getBags(player);
			
			if (store(bags, ingredients != null ? ingredients.copy() : null, unit != null ? unit.copy() : null, true) && !simulate)
				store(bags, ingredients, unit, false);
		}
		
		return true;
	}
	
	public static boolean addIngredients(List<ItemStack> bags, BlockIngredients ingredients, ColorUnit unit) throws NotEnoughIngredientsException {
		if (store(bags, ingredients != null ? ingredients.copy() : null, unit != null ? unit.copy() : null, true))
			store(bags, ingredients, unit, false);
		
		return true;
	}
	
	public static List<ItemStack> getBags(EntityPlayer player) {
		List<ItemStack> bags = new ArrayList<>();
		for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
			ItemStack stack = player.inventory.getStackInSlot(i);
			if (stack.getItem() instanceof ItemBag)
				bags.add(stack);
		}
		return bags;
	}
	
	public static boolean canDrainPremadeItemStack(EntityPlayer player, ItemStack toDrain) throws NotEnoughIngredientsException {
		if (!needIngredients(player))
			return true;
		
		String id = ItemPremadeStructure.getPremadeID(toDrain);
		for (ItemStack stack : player.inventory.mainInventory) {
			if (stack.getItem() == LittleTiles.premade && ItemPremadeStructure.getPremadeID(stack).equals(id))
				return true;
		}
		throw new NotEnoughIngredientsException.NotEnoughStackException(toDrain);
	}
	
	public static void drainPremadeItemStack(EntityPlayer player, ItemStack toDrain) throws NotEnoughIngredientsException {
		if (!needIngredients(player))
			return;
		
		String id = ItemPremadeStructure.getPremadeID(toDrain);
		for (ItemStack stack : player.inventory.mainInventory) {
			if (stack.getItem() == LittleTiles.premade && ItemPremadeStructure.getPremadeID(stack).equals(id)) {
				stack.shrink(1);
				return;
			}
		}
		throw new NotEnoughIngredientsException.NotEnoughStackException(toDrain);
	}
	
	@SideOnly(Side.CLIENT)
	public static boolean doesBlockSupportedTranslucent(Block block) {
		return block.getBlockLayer() == BlockRenderLayer.SOLID || block.getBlockLayer() == BlockRenderLayer.TRANSLUCENT;
	}
	
	public static boolean isBlockValid(Block block) {
		return block.isNormalCube(block.getDefaultState()) || block.isFullCube(block.getDefaultState()) || block.isFullBlock(block.getDefaultState()) || block instanceof BlockGlass || block instanceof BlockStainedGlass || block instanceof BlockBreakable;
	}
	
}
