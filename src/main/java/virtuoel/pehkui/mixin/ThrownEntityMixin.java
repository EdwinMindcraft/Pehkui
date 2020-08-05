package virtuoel.pehkui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.util.ScaleUtils;

@Mixin(ThrownEntity.class)
public abstract class ThrownEntityMixin extends EntityMixin
{
	@Inject(at = @At("RETURN"), method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;)V")
	private void onConstruct(EntityType<? extends ThrownEntity> type, LivingEntity owner, World world, CallbackInfo info)
	{
		final float heightScale = ScaleUtils.getHeightScale(owner);
		if (heightScale != 1.0F)
		{
			final Vec3d pos = ((Entity) (Object) this).getPos();
			
			updatePosition(pos.x, pos.y + ((1.0F - heightScale) * 0.1D), pos.z);
		}
		
		final float scale = ScaleUtils.getProjectileScale(owner);
		if (scale != 1.0F)
		{
			final ScaleData scaleData = pehkui_getScaleData(ScaleType.BASE);
			
			scaleData.setScale(scale);
			scaleData.setTargetScale(scale);
			scaleData.markForSync();
		}
	}
}
