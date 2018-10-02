package com.creativemd.littletiles.common.structure.connection;

import com.creativemd.creativecore.common.utils.mc.WorldUtils;
import com.creativemd.littletiles.common.structure.LittleStructure;
import com.creativemd.littletiles.common.structure.attribute.LittleStructureAttribute;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.tiles.LittleTile;
import com.creativemd.littletiles.common.tiles.vec.LittleTileIdentifierStructureRelative;
import com.creativemd.littletiles.common.utils.grid.LittleGridContext;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public abstract class StructureLinkBaseRelative<T> extends LittleTileIdentifierStructureRelative implements IStructureConnector<T> {
	
	protected LittleStructure structure;
	protected final T parent;
	
	public StructureLinkBaseRelative(TileEntity te, BlockPos coord, LittleGridContext context, int[] identifier, LittleStructureAttribute attribute, T parent) {
		super(te, coord, context, identifier, attribute);
		this.parent = parent;
	}
	
	public StructureLinkBaseRelative(BlockPos origin, BlockPos coord, LittleGridContext context, int[] identifier, LittleStructureAttribute attribute, T parent) {
		super(origin, coord, context, identifier, attribute);
		this.parent = parent;
	}
	
	public StructureLinkBaseRelative(int baseX, int baseY, int baseZ, BlockPos coord, LittleGridContext context, int[] identifier, LittleStructureAttribute attribute, T parent) {
		super(baseX, baseY, baseZ, coord, context, identifier, attribute);
		this.parent = parent;
	}
	
	public StructureLinkBaseRelative(NBTTagCompound nbt, T parent) {
		super(nbt);
		this.parent = parent;
	}
	
	protected StructureLinkBaseRelative(int relativeX, int relativeY, int relativeZ, LittleGridContext context, int[] identifier, LittleStructureAttribute attribute, T parent) {
		super(relativeX, relativeY, relativeZ, context, identifier, attribute);
		this.parent = parent;
	}
	
	protected abstract void connect(World world, LittleTile mainTile);
	
	protected abstract void failedConnect(World world);
	
	protected boolean loadingStructure;
	
	@Override
	public LittleStructure getStructureWithoutLoading() {
		return structure;
	}
	
	@Override
	public boolean isConnected(World world) {
		if (loadingStructure) {
			new RuntimeException("Attempted to load structure twice!").printStackTrace();
			return false;
		}
		
		if (structure != null)
			return true;
		
		if (world == null)
			return false;
		
		loadingStructure = true;
		
		BlockPos absoluteCoord = getStructurePosition();
		Chunk chunk = world.getChunkFromBlockCoords(absoluteCoord);
		if (WorldUtils.checkIfChunkExists(chunk)) {
			TileEntity te = world.getTileEntity(absoluteCoord);
			if (te instanceof TileEntityLittleTiles) {
				LittleTile tile = ((TileEntityLittleTiles) te).getTile(context, identifier);
				if (tile != null && tile.isChildOfStructure() && !tile.connection.isLink()) {
					connect(world, tile);
				}
			}
			
			if (structure == null && !world.isRemote) {
				failedConnect(world);
			}
			
			loadingStructure = false;
			
			return structure != null;
		}
		
		loadingStructure = false;
		
		return false;
		
	}
	
	@Override
	public LittleStructure getStructure(World world) {
		if (isConnected(world))
			return structure;
		return null;
	}
	
	@Override
	public void setLoadedStructure(LittleStructure structure, LittleStructureAttribute attribute) {
		this.structure = structure;
		this.attribute = attribute;
	}
	
	@Override
	public void reset() {
		this.structure = null;
	}
	
	@Override
	public boolean isLink() {
		return true;
	}
	
	@Override
	public boolean is(LittleTile mainTile) {
		return mainTile.is(context, identifier);
	}
	
	@Override
	public LittleStructureAttribute getAttribute() {
		return attribute;
	}
	
}