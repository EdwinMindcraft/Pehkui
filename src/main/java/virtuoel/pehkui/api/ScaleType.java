package virtuoel.pehkui.api;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import virtuoel.pehkui.Pehkui;
import virtuoel.pehkui.entity.ResizableEntity;
import virtuoel.pehkui.mixin.EntityAccessor;
import virtuoel.pehkui.util.ScaleUtils;

public class ScaleType
{
	public static final ScaleType INVALID = register(ScaleRegistries.getDefaultId(ScaleRegistries.SCALE_TYPES));
	public static final ScaleType BASE = registerBaseScale("base");
	public static final ScaleType WIDTH = registerDimensionScale("width");
	public static final ScaleType HEIGHT = registerDimensionScale("height");
	public static final ScaleType MOTION = register("motion", ScaleModifier.BASE_MULTIPLIER);
	public static final ScaleType REACH = register("reach", ScaleModifier.BASE_MULTIPLIER);
	public static final ScaleType ATTACK = register("attack");
	public static final ScaleType DEFENSE = register("defense");
	public static final ScaleType HEALTH = register("health");
	public static final ScaleType DROPS = register("drops", ScaleModifier.BASE_MULTIPLIER);
	public static final ScaleType PROJECTILES = register("projectiles", ScaleModifier.BASE_MULTIPLIER);
	public static final ScaleType EXPLOSIONS = register("explosions", ScaleModifier.BASE_MULTIPLIER);
	
	/**
	 * @see {@link ScaleType.Builder}
	 */
	private ScaleType(Builder builder)
	{
		this(builder.defaultBaseScale, builder.defaultTickDelay, builder.defaultBaseValueModifiers);
	}
	
	/**
	 * @see {@link ScaleType.Builder}
	 */
	private ScaleType(float defaultBaseScale, int defaultTickDelay, Set<ScaleModifier> defaultBaseValueModifiers)
	{
		this.defaultBaseScale = defaultBaseScale;
		this.defaultTickDelay = defaultTickDelay;
		this.defaultBaseValueModifiers = defaultBaseValueModifiers;
	}
	
	public ScaleData getScaleData(Entity entity)
	{
		return ((ResizableEntity) entity).pehkui_getScaleData(this);
	}
	
	private float defaultBaseScale;
	
	public final float getDefaultBaseScale()
	{
		return defaultBaseScale;
	}
	
	private int defaultTickDelay;
	
	public final int getDefaultTickDelay()
	{
		return defaultTickDelay;
	}
	
	private final Set<ScaleModifier> defaultBaseValueModifiers;
	
	/**
	 * Returns a mutable sorted set of scale modifiers. These modifiers are applied to all scale data of this type.
	 * @return Set of scale modifiers sorted by priority
	 */
	public Set<ScaleModifier> getDefaultBaseValueModifiers()
	{
		return defaultBaseValueModifiers;
	}
	
	public static class Builder
	{
		private Set<ScaleModifier> defaultBaseValueModifiers = new ObjectRBTreeSet<>();
		private float defaultBaseScale = 1.0F;
		private int defaultTickDelay = 20;
		private boolean affectsDimensions = false;
		private Set<ScaleModifier> dependentModifiers = new ObjectRBTreeSet<>();
		
		public static Builder create()
		{
			return new Builder();
		}
		
		private Builder()
		{
			
		}
		
		public void defaultBaseScale(float defaultBaseScale)
		{
			this.defaultBaseScale = defaultBaseScale;
		}
		
		public void defaultTickDelay(int defaultTickDelay)
		{
			this.defaultTickDelay = defaultTickDelay;
		}
		
		public Builder addBaseValueModifier(ScaleModifier scaleModifier)
		{
			this.defaultBaseValueModifiers.add(scaleModifier);
			return this;
		}
		
		public Builder affectsDimensions()
		{
			this.affectsDimensions = true;
			return this;
		}
		
		public Builder addDependentModifier(ScaleModifier scaleModifier)
		{
			this.dependentModifiers.add(scaleModifier);
			return this;
		}
		
		public ScaleType build()
		{
			final ScaleType type = new ScaleType(this);
			
			if (this.affectsDimensions)
			{
				type.getScaleChangedEvent().register(Builder::calculateDimensions);
			}
			
			if (!this.dependentModifiers.isEmpty())
			{
				type.getScaleChangedEvent().register(createModifiedDataSyncEvent(this.dependentModifiers));
			}
			
			return type;
		}
		
		private static void calculateDimensions(ScaleData s)
		{
			final Entity e = s.getEntity();
			
			if (e != null)
			{
				final EntityAccessor en = (EntityAccessor) e;
				final boolean onGround = en.getOnGround();
				
				if (!ScaleUtils.isAboveCollisionThreshold(e))
				{
					e.calculateDimensions();
				}
				
				en.setOnGround(onGround);
			}
		}
		
		private static ScaleEventCallback createModifiedDataSyncEvent(final Collection<ScaleModifier> modifiers)
		{
			return s ->
			{
				final Entity e = s.getEntity();
				
				if (e != null)
				{
					ScaleData data;
					for (ScaleType scaleType : ScaleRegistries.SCALE_TYPES.values())
					{
						data = scaleType.getScaleData(e);
						
						if (!Collections.disjoint(modifiers, data.getBaseValueModifiers()))
						{
							data.markForSync(true);
						}
					}
				}
			};
		}
	}
	
	private final Event<ScaleEventCallback> scaleChangedEvent = createScaleEvent();
	
	public Event<ScaleEventCallback> getScaleChangedEvent()
	{
		return scaleChangedEvent;
	}
	
	private final Event<ScaleEventCallback> preTickEvent = createScaleEvent();
	
	public Event<ScaleEventCallback> getPreTickEvent()
	{
		return preTickEvent;
	}
	
	private final Event<ScaleEventCallback> postTickEvent = createScaleEvent();
	
	public Event<ScaleEventCallback> getPostTickEvent()
	{
		return postTickEvent;
	}
	
	private static Event<ScaleEventCallback> createScaleEvent()
	{
		return EventFactory.createArrayBacked(
			ScaleEventCallback.class,
			data -> {},
			(callbacks) -> (data) ->
			{
				for (ScaleEventCallback callback : callbacks)
				{
					callback.onEvent(data);
				}
			}
		);
	}
	
	private static ScaleType register(Identifier id, Builder builder)
	{
		return ScaleRegistries.register(
			ScaleRegistries.SCALE_TYPES,
			id,
			builder.build()
		);
	}
	
	private static ScaleType register(Identifier id)
	{
		final Builder builder = Builder.create();
		
		return register(id, builder);
	}
	
	private static ScaleType register(String path, ScaleModifier... modifiers)
	{
		final Builder builder = Builder.create();
		
		for (ScaleModifier scaleModifier : modifiers)
		{
			builder.addBaseValueModifier(scaleModifier);
		}
		
		return register(Pehkui.id(path), builder);
	}
	
	private static ScaleType registerBaseScale(String path)
	{
		final Builder builder = Builder.create()
			.affectsDimensions()
			.addDependentModifier(ScaleModifier.BASE_MULTIPLIER);
		
		return register(Pehkui.id(path), builder);
	}
	
	private static ScaleType registerDimensionScale(String path)
	{
		final Builder builder = Builder.create()
			.affectsDimensions()
			.addBaseValueModifier(ScaleModifier.BASE_MULTIPLIER);
		
		return register(Pehkui.id(path), builder);
	}
}
