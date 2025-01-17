package com.creativemd.littletiles.common.items;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.lwjgl.util.Color;

import com.creativemd.creativecore.client.rendering.RenderCubeObject;
import com.creativemd.creativecore.client.rendering.model.CreativeBakedModel;
import com.creativemd.creativecore.client.rendering.model.ICreativeRendered;
import com.creativemd.creativecore.common.gui.container.SubGui;
import com.creativemd.creativecore.common.gui.controls.gui.GuiButton;
import com.creativemd.creativecore.common.packet.PacketHandler;
import com.creativemd.creativecore.common.utils.math.Rotation;
import com.creativemd.creativecore.common.utils.mc.ColorUtils;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.client.render.PreviewRenderer;
import com.creativemd.littletiles.common.action.LittleAction;
import com.creativemd.littletiles.common.api.ILittleTile;
import com.creativemd.littletiles.common.blocks.BlockTile;
import com.creativemd.littletiles.common.container.SubContainerConfigure;
import com.creativemd.littletiles.common.gui.SubGuiChisel;
import com.creativemd.littletiles.common.gui.SubGuiMarkMode;
import com.creativemd.littletiles.common.gui.configure.SubGuiConfigure;
import com.creativemd.littletiles.common.gui.configure.SubGuiModeSelector;
import com.creativemd.littletiles.common.packet.LittleBlockPacket;
import com.creativemd.littletiles.common.packet.LittleBlockPacket.BlockPacketAction;
import com.creativemd.littletiles.common.packet.LittleVanillaBlockPacket;
import com.creativemd.littletiles.common.packet.LittleVanillaBlockPacket.VanillaBlockAction;
import com.creativemd.littletiles.common.tiles.LittleTile;
import com.creativemd.littletiles.common.tiles.LittleTileBlock;
import com.creativemd.littletiles.common.tiles.LittleTileBlockColored;
import com.creativemd.littletiles.common.tiles.preview.LittleAbsolutePreviews;
import com.creativemd.littletiles.common.tiles.preview.LittlePreviews;
import com.creativemd.littletiles.common.tiles.preview.LittleTilePreview;
import com.creativemd.littletiles.common.tiles.vec.LittleBoxes;
import com.creativemd.littletiles.common.tiles.vec.LittleTileBox;
import com.creativemd.littletiles.common.tiles.vec.LittleTilePos;
import com.creativemd.littletiles.common.utils.grid.LittleGridContext;
import com.creativemd.littletiles.common.utils.placing.MarkMode;
import com.creativemd.littletiles.common.utils.placing.PlacementHelper.PositionResult;
import com.creativemd.littletiles.common.utils.placing.PlacementMode;
import com.creativemd.littletiles.common.utils.shape.DragShape;
import com.creativemd.littletiles.common.utils.tooltip.TooltipUtils;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemLittleChisel extends Item implements ICreativeRendered, ILittleTile {
	
	public ItemLittleChisel() {
		setCreativeTab(LittleTiles.littleTab);
		hasSubtypes = true;
		setMaxStackSize(1);
	}
	
	@Override
	public float getDestroySpeed(ItemStack stack, IBlockState state) {
		return 0F;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		DragShape shape = getShape(stack);
		tooltip.add("shape: " + shape.key);
		shape.addExtraInformation(stack.getTagCompound(), tooltip);
		LittleTilePreview preview = ItemLittleGrabber.SimpleMode.getPreview(stack);
		tooltip.add(TooltipUtils.printRGB(preview.hasColor() ? preview.getColor() : ColorUtils.WHITE));
	}
	
	public static PositionResult min;
	
	@SideOnly(Side.CLIENT)
	public static PositionResult lastMax;
	
	@Override
	public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player) {
		return false;
	}
	
	public static DragShape getShape(ItemStack stack) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		return DragShape.getShape(stack.getTagCompound().getString("shape"));
	}
	
	public static void setShape(ItemStack stack, DragShape shape) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		stack.getTagCompound().setString("shape", shape.key);
	}
	
	public static LittleTilePreview getPreview(ItemStack stack) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		if (stack.getTagCompound().hasKey("preview"))
			return LittleTilePreview.loadPreviewFromNBT(stack.getTagCompound().getCompoundTag("preview"));
		
		IBlockState state = stack.getTagCompound().hasKey("state") ? Block.getStateById(stack.getTagCompound().getInteger("state")) : Blocks.STONE.getDefaultState();
		LittleTile tile = stack.getTagCompound().hasKey("color") ? new LittleTileBlockColored(state.getBlock(), state.getBlock().getMetaFromState(state), stack.getTagCompound().getInteger("color")) : new LittleTileBlock(state.getBlock(), state.getBlock().getMetaFromState(state));
		tile.box = new LittleTileBox(LittleGridContext.get().minPos, LittleGridContext.get().minPos, LittleGridContext.get().minPos, LittleGridContext.get().size, LittleGridContext.get().size, LittleGridContext.get().size);
		LittleTilePreview preview = tile.getPreviewTile();
		setPreview(stack, preview);
		return preview;
	}
	
	public static void setPreview(ItemStack stack, LittleTilePreview preview) {
		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		NBTTagCompound nbt = new NBTTagCompound();
		preview.writeToNBT(nbt);
		stack.getTagCompound().setTag("preview", nbt);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public List<RenderCubeObject> getRenderingCubes(IBlockState state, TileEntity te, ItemStack stack) {
		return Collections.emptyList();
	}
	
	@SideOnly(Side.CLIENT)
	IBakedModel model;
	
	@Override
	@SideOnly(Side.CLIENT)
	public void applyCustomOpenGLHackery(ItemStack stack, TransformType cameraTransformType) {
		Minecraft mc = Minecraft.getMinecraft();
		GlStateManager.pushMatrix();
		
		if (model == null)
			model = mc.getRenderItem().getItemModelMesher().getModelManager().getModel(new ModelResourceLocation(LittleTiles.modid + ":chisel_background", "inventory"));
		ForgeHooksClient.handleCameraTransforms(model, cameraTransformType, false);
		
		mc.getRenderItem().renderItem(new ItemStack(Items.PAPER), model);
		
		if (cameraTransformType == TransformType.GUI) {
			GlStateManager.translate(0.1, 0.1, 0);
			GlStateManager.scale(0.7, 0.7, 0.7);
			
			LittleTilePreview preview = getPreview(stack);
			ItemStack blockStack = new ItemStack(preview.getPreviewBlock(), 1, preview.getPreviewBlockMeta());
			IBakedModel model = mc.getRenderItem().getItemModelWithOverrides(blockStack, mc.world, mc.player); // getItemModelMesher().getItemModel(blockStack);
			if (!(model instanceof CreativeBakedModel))
				ForgeHooksClient.handleCameraTransforms(model, cameraTransformType, false);
			
			GlStateManager.disableDepth();
			GlStateManager.pushMatrix();
			GlStateManager.translate(-0.5F, -0.5F, -0.5F);
			
			try {
				if (model.isBuiltInRenderer()) {
					GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
					GlStateManager.enableRescaleNormal();
					TileEntityItemStackRenderer.instance.renderByItem(blockStack);
				} else {
					Color color = preview.hasColor() ? ColorUtils.IntToRGBA(preview.getColor()) : ColorUtils.IntToRGBA(ColorUtils.WHITE);
					color.setAlpha(255);
					ReflectionHelper.findMethod(RenderItem.class, "renderModel", "func_191967_a", IBakedModel.class, int.class, ItemStack.class).invoke(mc.getRenderItem(), model, preview.hasColor() ? ColorUtils.RGBAToInt(color) : -1, blockStack);
				}
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
			
			GlStateManager.popMatrix();
			
			GlStateManager.enableDepth();
		}
		
		GlStateManager.popMatrix();
		
	}
	
	@Override
	public boolean hasLittlePreview(ItemStack stack) {
		return true;
	}
	
	private static LittleTilePos cachedPos;
	private static LittleBoxes cachedShape;
	private static boolean cachedLow;
	private static NBTTagCompound cachedSettings;
	
	@SideOnly(Side.CLIENT)
	private static EntityPlayer getPlayer() {
		return Minecraft.getMinecraft().player;
	}
	
	@Override
	public LittleAbsolutePreviews getLittlePreview(ItemStack stack) {
		return null;
	}
	
	@Override
	public LittleAbsolutePreviews getLittlePreview(ItemStack stack, boolean allowLowResolution, boolean marked) {
		PositionResult min = ItemLittleChisel.min;
		if (min == null)
			min = lastMax;
		if (min != null) {
			if (lastMax == null)
				lastMax = min.copy();
			
			min.ensureBothAreEqual(lastMax);
			
			LittleGridContext context = getPositionContext(stack);
			if (context.size < min.getContext().size)
				context = min.getContext();
			LittleTilePos offset = new LittleTilePos(min.pos, context);
			if (lastMax == null)
				lastMax = min.copy();
			
			LittleBoxes boxes = null;
			if (cachedPos == null || !cachedPos.equals(lastMax) || !cachedSettings.equals(stack.getTagCompound()) || cachedLow != allowLowResolution) {
				
				DragShape shape = getShape(stack);
				LittleTileBox newBox = new LittleTileBox(new LittleTileBox(min.getRelative(offset).getVec(context)), new LittleTileBox(lastMax.getRelative(offset).getVec(context)));
				boxes = shape.getBoxes(new LittleBoxes(offset.pos, context), newBox.getMinVec(), newBox.getMaxVec(), getPlayer(), stack.getTagCompound(), allowLowResolution, min, lastMax);
				cachedPos = lastMax.copy();
				cachedShape = boxes.copy();
				cachedSettings = stack.getTagCompound().copy();
				cachedLow = allowLowResolution;
			} else
				boxes = cachedShape;
			
			LittleAbsolutePreviews previews = new LittleAbsolutePreviews(offset.pos, boxes.context);
			
			LittleTilePreview preview = getPreview(stack);
			for (int i = 0; i < boxes.size(); i++) {
				LittleTilePreview newPreview = preview.copy();
				newPreview.box = boxes.get(i);
				previews.addWithoutCheckingPreview(newPreview);
			}
			
			return previews;
		}
		return null;
	}
	
	@Override
	public void saveLittlePreview(ItemStack stack, LittlePreviews previews) {
	}
	
	@Override
	public void rotateLittlePreview(EntityPlayer player, ItemStack stack, Rotation rotation) {
		getShape(stack).rotate(stack.getTagCompound(), rotation);
	}
	
	@Override
	public void flipLittlePreview(EntityPlayer player, ItemStack stack, Axis axis) {
		getShape(stack).flip(stack.getTagCompound(), axis);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public float getPreviewAlphaFactor() {
		return 0.4F;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void tickPreview(EntityPlayer player, ItemStack stack, PositionResult position, RayTraceResult result) {
		lastMax = getPosition(position, result, currentMode);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public boolean shouldCache() {
		return false;
	}
	
	@Override
	public void onDeselect(EntityPlayer player, ItemStack stack) {
		min = null;
		lastMax = null;
	}
	
	protected static PositionResult getPosition(PositionResult position, RayTraceResult result, PlacementMode mode) {
		position = position.copy();
		
		EnumFacing facing = position.facing;
		if (mode.placeInside)
			facing = facing.getOpposite();
		if (facing.getAxisDirection() == AxisDirection.NEGATIVE)
			position.contextVec.vec.add(facing);
		
		return position;
	}
	
	@Override
	public boolean onRightClick(World world, EntityPlayer player, ItemStack stack, PositionResult position, RayTraceResult result) {
		if (ItemLittleChisel.min == null) {
			ItemLittleChisel.min = getPosition(position, result, currentMode);
		} else if (LittleAction.isUsingSecondMode(player)) {
			ItemLittleChisel.min = null;
			ItemLittleChisel.lastMax = null;
			ItemLittleChisel.cachedPos = null;
			ItemLittleChisel.cachedSettings = null;
			ItemLittleChisel.cachedShape = null;
			PreviewRenderer.marked = null;
			
		} else
			return true;
		return false;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public boolean onMouseWheelClickBlock(World world, EntityPlayer player, ItemStack stack, RayTraceResult result) {
		IBlockState state = player.world.getBlockState(result.getBlockPos());
		if (LittleAction.isBlockValid(state.getBlock())) {
			PacketHandler.sendPacketToServer(new LittleVanillaBlockPacket(result.getBlockPos(), VanillaBlockAction.CHISEL));
			return true;
		} else if (state.getBlock() instanceof BlockTile) {
			PacketHandler.sendPacketToServer(new LittleBlockPacket(world, result.getBlockPos(), player, BlockPacketAction.CHISEL, new NBTTagCompound()));
			return true;
		}
		return false;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public SubGuiConfigure getConfigureGUI(EntityPlayer player, ItemStack stack) {
		return new SubGuiChisel(stack);
	}
	
	@Override
	public SubContainerConfigure getConfigureContainer(EntityPlayer player, ItemStack stack) {
		return new SubContainerConfigure(player, stack);
	}
	
	public static PlacementMode currentMode = PlacementMode.fill;
	
	@Override
	public PlacementMode getPlacementMode(ItemStack stack) {
		return currentMode;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public SubGuiConfigure getConfigureGUIAdvanced(EntityPlayer player, ItemStack stack) {
		return new SubGuiModeSelector(stack, ItemMultiTiles.currentContext, currentMode) {
			
			@Override
			public void saveConfiguration(LittleGridContext context, PlacementMode mode) {
				currentMode = mode;
				ItemMultiTiles.currentContext = context;
			}
		};
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public MarkMode onMark(EntityPlayer player, ItemStack stack) {
		if (min == null)
			if (lastMax != null)
				min = lastMax.copy();
			else
				return null;
			
		return new MarkMode() {
			
			@Override
			public SubGui getConfigurationGui() {
				return new SubGuiMarkMode(this) {
					@Override
					public void createControls() {
						super.createControls();
						controls.add(new GuiButton(I18n.translateToLocal("markmode.gui.switchpos"), 10, 20) {
							
							@Override
							public void onClicked(int x, int y, int button) {
								if (lastMax == null)
									lastMax = min.copy();
								
								mode.position = min;
								min = lastMax;
								lastMax = mode.position;
							}
						});
					}
				};
			}
		};
	}
	
	@Override
	public boolean containsIngredients(ItemStack stack) {
		return false;
	}
	
	@Override
	public LittleGridContext getPositionContext(ItemStack stack) {
		return ItemMultiTiles.currentContext;
	}
}
