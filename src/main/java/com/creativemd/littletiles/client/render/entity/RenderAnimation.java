package com.creativemd.littletiles.client.render.entity;

import java.util.Iterator;

import org.lwjgl.opengl.GL11;

import com.creativemd.creativecore.client.mods.optifine.OptifineHelper;
import com.creativemd.littletiles.client.LittleTilesClient;
import com.creativemd.littletiles.client.render.LittleRenderChunkSuppilier;
import com.creativemd.littletiles.common.entity.EntityAnimation;
import com.creativemd.littletiles.common.events.LittleEvent;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.utils.grid.LittleGridContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.optifine.shaders.ShadersRender;

public class RenderAnimation extends Render<EntityAnimation> {
	
	public static Minecraft mc = Minecraft.getMinecraft();
	public static final VertexBufferUploader uploader = new VertexBufferUploader();
	
	public RenderAnimation(RenderManager renderManager) {
		super(renderManager);
	}
	
	@Override
	public void doRender(EntityAnimation entity, double x, double y, double z, float entityYaw, float partialTicks) {
		super.doRender(entity, x, y, z, entityYaw, partialTicks);
		
		boolean first = MinecraftForgeClient.getRenderPass() == 0 || MinecraftForgeClient.getRenderPass() == -1;
		
		if (entity.isDead)
			return;
		
		LittleRenderChunkSuppilier suppilier = entity.getRenderChunkSuppilier();
		
		if (first) {
			synchronized (suppilier.renderChunks) {
				for (LittleRenderChunk chunk : suppilier.renderChunks.values()) {
					chunk.uploadBuffer();
				}
			}
		}
		
		GlStateManager.pushMatrix();
		
		entity.origin.setupRendering(entity, partialTicks);
		
		LittleGridContext context = entity.center.inBlockOffset.context;
		
		// SETUP OPENGL
		
		bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
		RenderHelper.disableStandardItemLighting();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		
		GlStateManager.enableCull();
		GlStateManager.enableTexture2D();
		
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		
		GlStateManager.glEnableClientState(32884);
		OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.glEnableClientState(32888);
		OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.glEnableClientState(32888);
		OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.glEnableClientState(32886);
		
		float f = (float) mc.getRenderViewEntity().posX;
		float f1 = (float) mc.getRenderViewEntity().posY + mc.getRenderViewEntity().getEyeHeight();
		float f2 = (float) mc.getRenderViewEntity().posZ;
		
		if (first) {
			GlStateManager.disableAlpha();
			renderBlockLayer(suppilier, BlockRenderLayer.SOLID, entity, f, f1, f2);
			GlStateManager.enableAlpha();
			mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, this.mc.gameSettings.mipmapLevels > 0); // FORGE: fix flickering leaves when mods mess up the blurMipmap settings
			renderBlockLayer(suppilier, BlockRenderLayer.CUTOUT_MIPPED, entity, f, f1, f2);
			this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
			this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
			renderBlockLayer(suppilier, BlockRenderLayer.CUTOUT, entity, f, f1, f2);
			this.mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
		} else {
			renderBlockLayer(suppilier, BlockRenderLayer.TRANSLUCENT, entity, f, f1, f2);
		}
		
		for (final VertexFormatElement vertexformatelement : DefaultVertexFormats.BLOCK.getElements()) {
			final VertexFormatElement.EnumUsage vertexformatelement$enumusage = vertexformatelement.getUsage();
			final int i1 = vertexformatelement.getIndex();
			
			switch (vertexformatelement$enumusage) {
			case POSITION:
				GlStateManager.glDisableClientState(32884);
				break;
			case UV:
				OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + i1);
				GlStateManager.glDisableClientState(32888);
				OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
				break;
			case COLOR:
				GlStateManager.glDisableClientState(32886);
				GlStateManager.resetColor();
			}
		}
		
		GlStateManager.shadeModel(GL11.GL_FLAT);
		
		if (!first) {
			GlStateManager.popMatrix();
			return;
		}
		
		// ===Render dynamic part===
		
		GlStateManager.enableRescaleNormal();
		// Setup OPENGL
		for (Iterator<TileEntity> iterator = entity.fakeWorld.loadedTileEntityList.iterator(); iterator.hasNext();) {
			TileEntity tileEntity = iterator.next();
			if (!(tileEntity instanceof TileEntityLittleTiles))
				continue;
			TileEntityLittleTiles te = (TileEntityLittleTiles) tileEntity;
			if (te.shouldRenderInPass(0)) {
				GlStateManager.pushMatrix();
				
				GlStateManager.translate(te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
				
				// Render TileEntity
				render(te, partialTicks, -1);
				
				GlStateManager.popMatrix();
			}
		}
		
		GlStateManager.popMatrix();
		
		RenderHelper.enableStandardItemLighting();
		
		if (!entity.shouldAddDoor()) {
			for (Entity child : entity.fakeWorld.loadedEntityList) {
				if (child instanceof EntityAnimation)
					doRender((EntityAnimation) child, x + (entity.posX - child.posX), y + (entity.posY - child.posY), z + (entity.posZ - child.posZ), entityYaw, partialTicks);
			}
		}
		
		if (mc.getRenderManager().isDebugBoundingBox() && !mc.isReducedDebug()) {
			GlStateManager.depthMask(false);
			GlStateManager.disableTexture2D();
			GlStateManager.disableLighting();
			GlStateManager.disableCull();
			GlStateManager.disableBlend();
			//GlStateManager.disableDepth();
			
			GlStateManager.glLineWidth(4.0F);
			
			GlStateManager.pushMatrix();
			
			AxisAlignedBB bb = entity.getEntityBoundingBox();
			GlStateManager.translate(-TileEntityRendererDispatcher.staticPlayerX, -TileEntityRendererDispatcher.staticPlayerY, -TileEntityRendererDispatcher.staticPlayerZ);
			RenderGlobal.drawBoundingBox(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
			
			GlStateManager.popMatrix();
			
			GlStateManager.enableDepth();
			GlStateManager.glLineWidth(2.0F);
			GlStateManager.enableTexture2D();
			GlStateManager.enableLighting();
			GlStateManager.enableCull();
			GlStateManager.disableBlend();
			GlStateManager.depthMask(true);
		}
	}
	
	public void render(TileEntityLittleTiles tileentityIn, float partialTicks, int destroyStage) {
		if (tileentityIn.getDistanceSq(TileEntityRendererDispatcher.instance.entityX, TileEntityRendererDispatcher.instance.entityY, TileEntityRendererDispatcher.instance.entityZ) < tileentityIn.getMaxRenderDistanceSquared()) {
			if (!tileentityIn.hasFastRenderer()) {
				RenderHelper.enableStandardItemLighting();
				int i = tileentityIn.getWorld().getCombinedLight(tileentityIn.getPos(), 0);
				int j = i % 65536;
				int k = i / 65536;
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j, k);
				GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			}
			BlockPos blockpos = tileentityIn.getPos();
			LittleTilesClient.tileEntityRenderer.render(tileentityIn, 0, 0, 0, partialTicks, destroyStage, 1.0F);
		}
	}
	
	public void renderBlockLayer(LittleRenderChunkSuppilier suppilier, BlockRenderLayer layer, EntityAnimation entity, float f, float f1, float f2) {
		
		if (FMLClientHandler.instance().hasOptifine() && OptifineHelper.isShaders())
			ShadersRender.preRenderChunkLayer(layer);
		
		synchronized (suppilier.renderChunks) {
			for (LittleRenderChunk chunk : suppilier.renderChunks.values()) {
				
				if (layer == BlockRenderLayer.TRANSLUCENT)
					chunk.resortTransparency(LittleEvent.transparencySortingIndex, f, f1, f2);
				
				VertexBuffer buffer = chunk.getLayerBuffer(layer);
				
				if (buffer == null)
					continue;
				
				// Render buffer
				GlStateManager.pushMatrix();
				
				mc.entityRenderer.enableLightmap();
				
				GlStateManager.translate(chunk.pos.getX() * 16, chunk.pos.getY() * 16, chunk.pos.getZ() * 16);
				
				if (layer == BlockRenderLayer.TRANSLUCENT) {
					GlStateManager.enableBlend();
					GlStateManager.disableAlpha();
				} else {
					GlStateManager.disableBlend();
					GlStateManager.enableAlpha();
				}
				
				buffer.bindBuffer();
				
				if (FMLClientHandler.instance().hasOptifine() && OptifineHelper.isShaders())
					ShadersRender.setupArrayPointersVbo();
				else {
					GlStateManager.glVertexPointer(3, 5126, 28, 0);
					GlStateManager.glColorPointer(4, 5121, 28, 12);
					GlStateManager.glTexCoordPointer(2, 5126, 28, 16);
					OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
					GlStateManager.glTexCoordPointer(2, 5122, 28, 24);
					OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
				}
				
				buffer.drawArrays(GL11.GL_QUADS);
				buffer.unbindBuffer();
				
				mc.entityRenderer.disableLightmap();
				
				GlStateManager.popMatrix();
				
			}
		}
		
		if (FMLClientHandler.instance().hasOptifine() && OptifineHelper.isShaders())
			ShadersRender.postRenderChunkLayer(layer);
	}
	
	@Override
	protected ResourceLocation getEntityTexture(EntityAnimation entity) {
		return TextureMap.LOCATION_BLOCKS_TEXTURE;
	}
	
	public static BlockPos getRenderChunkPos(BlockPos blockPos) {
		return new BlockPos(blockPos.getX() >> 4, blockPos.getY() >> 4, blockPos.getZ() >> 4);
	}
}
