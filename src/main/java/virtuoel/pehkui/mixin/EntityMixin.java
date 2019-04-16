package virtuoel.pehkui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.client.network.packet.CustomPayloadS2CPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import virtuoel.pehkui.Pehkui;
import virtuoel.pehkui.PehkuiClient;
import virtuoel.pehkui.api.ResizableEntity;

@Mixin(Entity.class)
public abstract class EntityMixin implements ResizableEntity
{
	@Shadow EntitySize size;
	@Shadow World world;
	@Shadow float stepHeight;
	
	@Shadow public abstract void refreshSize();
	
	private static final int DEFAULT_SCALING_TICK_TIME = 20;
	
	float scale = 1.0F;
	float prevScale = 1.0F;
	float fromScale = 1.0F;
	float toScale = 1.0F;
	int scaleTicks = 0;
	int totalScaleTicks = DEFAULT_SCALING_TICK_TIME;
	boolean scaleModified = false;
	
	@Inject(at = @At("HEAD"), method = "fromTag")
	private void onFromTag(CompoundTag compoundTag_1, CallbackInfo info)
	{
		if(compoundTag_1.containsKey(Pehkui.MOD_ID + ":scale_data", NbtType.COMPOUND))
		{
			final CompoundTag scaleData = compoundTag_1.getCompound(Pehkui.MOD_ID + ":scale_data");
			
			this.scale = scaleData.containsKey("scale") ? scaleData.getFloat("scale") : 1.0F;
			this.prevScale = this.scale;
			this.fromScale = scaleData.containsKey("initial") ? scaleData.getFloat("initial") : this.scale;
			this.toScale = scaleData.containsKey("target") ? scaleData.getFloat("target") : this.scale;
			this.scaleTicks = scaleData.containsKey("ticks") ? scaleData.getInt("ticks") : 0;
			this.totalScaleTicks = scaleData.containsKey("total_ticks") ? scaleData.getInt("total_ticks") : DEFAULT_SCALING_TICK_TIME;
			
			refreshSize();
			
			if(scale != 1.0F)
			{
				scheduleScaleUpdate();
			}
		}
	}
	
	@Inject(at = @At("HEAD"), method = "toTag")
	private void onToTag(CompoundTag compoundTag_1, CallbackInfoReturnable<CompoundTag> info)
	{
		final CompoundTag scaleData = new CompoundTag();
		
		scaleData.putFloat("scale", getScale());
		scaleData.putFloat("initial", getInitialScale());
		scaleData.putFloat("target", getTargetScale());
		scaleData.putInt("ticks", this.scaleTicks);
		scaleData.putInt("total_ticks", this.totalScaleTicks);
		
		compoundTag_1.put(Pehkui.MOD_ID + ":scale_data", scaleData);
	}
	
	@Inject(at = @At("HEAD"), method = "tick")
	private void onTickPre(CallbackInfo info)
	{
		tickScaleHook();
	}
	
	protected void tickScaleHook()
	{
		final float currScale = getScale();
		if(currScale != getTargetScale())
		{
			this.prevScale = currScale;
			if(this.scaleTicks >= this.totalScaleTicks)
			{
				this.fromScale = this.toScale;
				this.scaleTicks = 0;
				setScale(this.toScale);
			}
			else
			{
				this.scaleTicks++;
				final float nextScale = this.scale + ((this.toScale - this.fromScale) / (float) this.totalScaleTicks);
				setScale(nextScale);
			}
		}
		else if(this.prevScale != currScale)
		{
			this.prevScale = currScale;
		}
	}
	
	@Inject(at = @At("HEAD"), method = "onStartedTrackingBy")
	private void onOnStartedTrackingBy(ServerPlayerEntity serverPlayerEntity_1, CallbackInfo info)
	{
		if(getScale() != 1.0F)
		{
			serverPlayerEntity_1.networkHandler.sendPacket(new CustomPayloadS2CPacket(PehkuiClient.SCALE_PACKET, scaleToPacketByteBuf(new PacketByteBuf(Unpooled.buffer()).writeUuid(((Entity) (Object) this).getUuid()))));
		}
	}
	
	@Inject(at = @At("RETURN"), method = "getSize", cancellable = true)
	private void onGetSize(EntityPose entityPose_1, CallbackInfoReturnable<EntitySize> info)
	{
		final float scale = getScale();
		if(scale != 1.0F)
		{
			info.setReturnValue(info.getReturnValue().scaled(scale));
		}
	}
	
	@Redirect(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;setToDefaultPickupDelay()V"))
	public void onDropStackSetToDefaultPickupDelayProxy(ItemEntity obj)
	{
		final float scale = getScale();
		if(scale != 1.0F)
		{
			((ResizableEntity) obj).setScale(scale);
			((ResizableEntity) obj).setTargetScale(scale);
		}
		obj.setToDefaultPickupDelay();
	}
	
	@Shadow abstract Vec3d clipSneakingMovement(Vec3d vec3d_1, MovementType movementType_1);
	
	@Redirect(method = "move", at = @At(value = "INVOKE", target = "net/minecraft/entity/Entity.clipSneakingMovement(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/MovementType;)Lnet/minecraft/util/math/Vec3d;"))
	public Vec3d onMoveClipSneakingMovementProxy(Entity obj, Vec3d vec3d_1, MovementType movementType_1)
	{
		return clipSneakingMovement(movementType_1 == MovementType.SELF || movementType_1 == MovementType.PLAYER ? vec3d_1.multiply(getScale()) : vec3d_1, movementType_1);
	}
	
	@Inject(at = @At("HEAD"), method = "spawnSprintingParticles", cancellable = true)
	private void onSpawnSprintingParticles(CallbackInfo info)
	{
		if(getScale() < 1.0F)
		{
			info.cancel();
		}
	}
	
	@Redirect(method = "clipSneakingMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;stepHeight:F"))
	public float onClipSneakingMovementStepHeightProxy(Entity obj)
	{
		return stepHeight * getScale();
	}
	
	@Redirect(method = "method_17835", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;stepHeight:F"))
	public float onMethod_17835StepHeightProxy(Entity obj)
	{
		return stepHeight * getScale();
	}
	
	@Shadow abstract void setPosition(double double_1, double double_2, double double_3);
	
	@Override
	public float getScale()
	{
		return this.scale;
	}
	
	@Override
	public void setScale(float scale)
	{
		this.prevScale = this.scale;
		this.scale = scale;
		refreshSize();
	}
	
	@Override
	public float getInitialScale()
	{
		return this.fromScale;
	}
	
	@Override
	public float getTargetScale()
	{
		return this.toScale;
	}
	
	@Override
	public void setTargetScale(float targetScale)
	{
		this.fromScale = this.scale;
		this.toScale = targetScale;
		this.scaleTicks = 0;
	}
	
	@Override
	public int getScaleTickDelay()
	{
		return this.totalScaleTicks;
	}
	
	@Override
	public void setScaleTickDelay(int ticks)
	{
		this.totalScaleTicks = ticks;
	}
	
	@Override
	public float getPrevScale()
	{
		return this.prevScale;
	}
	
	@Override
	public void scheduleScaleUpdate()
	{
		if(world != null && !world.isClient)
		{
			this.scaleModified = true;
		}
	}
	
	@Override
	public boolean shouldSyncScale()
	{
		return this.scaleModified;
	}
	
	@Override
	public PacketByteBuf scaleToPacketByteBuf(PacketByteBuf buffer)
	{
		scaleModified = false;
		buffer.writeFloat(this.scale)
		.writeFloat(this.prevScale)
		.writeFloat(this.fromScale)
		.writeFloat(this.toScale)
		.writeInt(this.scaleTicks)
		.writeInt(this.totalScaleTicks);
		return buffer;
	}
	
	@Override
	public void scaleFromPacketByteBuf(PacketByteBuf buffer)
	{
		this.scale = buffer.readFloat();
		this.prevScale = buffer.readFloat();
		this.fromScale = buffer.readFloat();
		this.toScale = buffer.readFloat();
		this.scaleTicks = buffer.readInt();
		this.totalScaleTicks = buffer.readInt();
		refreshSize();
	}
}