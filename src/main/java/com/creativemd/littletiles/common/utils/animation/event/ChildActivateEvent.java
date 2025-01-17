package com.creativemd.littletiles.common.utils.animation.event;

import com.creativemd.creativecore.common.gui.container.GuiParent;
import com.creativemd.creativecore.common.utils.type.UUIDSupplier;
import com.creativemd.littletiles.common.entity.DoorController;
import com.creativemd.littletiles.common.entity.EntityAnimation;
import com.creativemd.littletiles.common.entity.EntityAnimationController;
import com.creativemd.littletiles.common.structure.IAnimatedStructure;
import com.creativemd.littletiles.common.structure.LittleStructure;
import com.creativemd.littletiles.common.structure.connection.IStructureChildConnector;
import com.creativemd.littletiles.common.structure.registry.LittleStructureGuiParser;
import com.creativemd.littletiles.common.structure.registry.LittleStructureRegistry;
import com.creativemd.littletiles.common.structure.type.door.LittleDoor;
import com.creativemd.littletiles.common.structure.type.door.LittleDoor.DoorOpeningResult;
import com.creativemd.littletiles.common.tiles.preview.LittlePreviews;
import com.creativemd.littletiles.common.utils.animation.AnimationGuiHandler;
import com.creativemd.littletiles.common.utils.animation.AnimationGuiHandler.AnimationGuiHolder;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ChildActivateEvent extends AnimationEvent {
	
	public int childId;
	
	public ChildActivateEvent(int tick, int childId) {
		super(tick);
		this.childId = childId;
	}
	
	public ChildActivateEvent(int tick) {
		super(tick);
	}
	
	@Override
	protected void write(NBTTagCompound nbt) {
		nbt.setInteger("childId", childId);
	}
	
	@Override
	protected void read(NBTTagCompound nbt) {
		childId = nbt.getInteger("childId");
	}
	
	@Override
	protected boolean run(EntityAnimationController controller) {
		LittleStructure structure = controller.parent.structure;
		IStructureChildConnector connector = structure.children.get(childId);
		LittleDoor door = (LittleDoor) connector.getStructure(structure.getWorld());
		DoorOpeningResult result;
		if (controller instanceof DoorController) {
			if (((DoorController) controller).result.isEmpty() || !((DoorController) controller).result.nbt.hasKey("c" + door.parent.getChildID()))
				result = LittleDoor.EMPTY_OPENING_RESULT;
			else
				result = new DoorOpeningResult(((DoorController) controller).result.nbt.getCompoundTag("c" + door.parent.getChildID()));
		} else
			result = LittleDoor.EMPTY_OPENING_RESULT;
		
		if (!door.canOpenDoor(null, result))
			result = door.canOpenDoor(null);
		if (result == null)
			return true;
		
		EntityAnimation childAnimation = door.openDoor(null, ((DoorController) controller).supplier, result);
		if (childAnimation != null)
			childAnimation.controller.onServerApproves();
		return true;
	}
	
	@Override
	public int getEventDuration(LittleStructure structure) {
		IStructureChildConnector connector = structure.children.get(childId);
		LittleDoor door = (LittleDoor) connector.getStructure(structure.getWorld());
		return door.getCompleteDuration();
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void prepareInGui(LittlePreviews previews, EntityAnimation animation, AnimationGuiHandler handler) {
		if (animation.structure.children.size() <= childId)
			return;
		IStructureChildConnector connector = animation.structure.children.get(childId);
		if (connector != null && connector.isConnected(animation.world) && connector.getStructureWithoutLoading() instanceof LittleDoor) {
			LittleDoor child = (LittleDoor) connector.getStructureWithoutLoading();
			EntityAnimation childAnimation;
			if (!connector.isLinkToAnotherWorld())
				childAnimation = child.openDoor(null, new UUIDSupplier(), LittleDoor.EMPTY_OPENING_RESULT);
			else
				childAnimation = ((IAnimatedStructure) child).getAnimation();
			GuiParent parent = new GuiParent("temp", 0, 0, 0, 0) {
			};
			AnimationGuiHolder holder = new AnimationGuiHolder(previews.getChildren().get(childId), new AnimationGuiHandler(getTick(), handler), childAnimation);
			holder.handler.takeInitialState(childAnimation);
			LittleStructureGuiParser parser = LittleStructureRegistry.getParser(parent, holder.handler, LittleStructureRegistry.getParserClass("structure." + child.type.id + ".name"));
			parser.createControls(holder.previews, holder.previews.getStructure());
			if (holder.handler.hasTimeline())
				handler.subHolders.add(holder);
		}
	}
	
}
