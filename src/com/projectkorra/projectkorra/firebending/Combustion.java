package com.projectkorra.projectkorra.firebending;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AvatarState;
import com.projectkorra.projectkorra.airbending.AirMethods;
import com.projectkorra.projectkorra.configuration.ConfigLoadable;
import com.projectkorra.projectkorra.util.ParticleEffect;

public class Combustion implements ConfigLoadable {

	public static long chargeTime = config.get().getLong("Abilities.Fire.Combustion.ChargeTime");
	public static long cooldown = config.get().getLong("Abilities.Fire.Combustion.Cooldown");

	public static double speed = config.get().getDouble("Abilities.Fire.Combustion.Speed");
	public static double defaultrange = config.get().getDouble("Abilities.Fire.Combustion.Range");
	public static double defaultpower = config.get().getDouble("Abilities.Fire.Combustion.Power");
	public static boolean breakblocks = config.get().getBoolean("Abilities.Fire.Combustion.BreakBlocks");
	public static double radius = config.get().getDouble("Abilities.Fire.Combustion.Radius");
	public static double defaultdamage = config.get().getDouble("Abilities.Fire.Combustion.Damage");

	public static ConcurrentHashMap<Player, Combustion> instances = new ConcurrentHashMap<>();

	private static final int maxticks = 10000;

	private Location location;
	private Location origin;
	private Player player;
	private Vector direction;
	private double range = defaultrange;
	private double speedfactor;
	private int ticks = 0;
	private float power;
	private double damage;
	@SuppressWarnings("unused")
	private long starttime;
	@SuppressWarnings("unused")
	private boolean charged = false;

	public Combustion(Player player) {
		/* Initial Checks */
		BendingPlayer bPlayer = GeneralMethods.getBendingPlayer(player.getName());
		if (instances.containsKey(player))
			return;
		if (bPlayer.isOnCooldown("Combustion"))
			return;
		/* End Initial Checks */
		// reloadVariables();
		this.player = player;
		starttime = System.currentTimeMillis();
		origin = player.getEyeLocation();
		direction = player.getEyeLocation().getDirection().normalize();
		location = origin.clone();
		if (AvatarState.isAvatarState(player)) {
			range = AvatarState.getValue(defaultrange);
			damage = AvatarState.getValue(defaultdamage);
		} else if (FireMethods.isDay(player.getWorld())) {
			range = FireMethods.getFirebendingDayAugment(defaultrange, player.getWorld());
			damage = FireMethods.getFirebendingDayAugment(defaultdamage, player.getWorld());
		} else {
			range = defaultrange;
			damage = defaultdamage;
		}

		if (GeneralMethods.isRegionProtectedFromBuild(player, "Combustion", GeneralMethods.getTargetedLocation(player, range))) {
			return;
		}

		instances.put(player, this);
		bPlayer.addCooldown("Combustion", cooldown);
	}

	public static void explode(Player player) {
		if (instances.containsKey(player)) {
			Combustion combustion = instances.get(player);
			combustion.createExplosion(combustion.location, combustion.power, breakblocks);
			ParticleEffect.EXPLODE.display(combustion.location, (float) Math.random(), (float) Math.random(),
					(float) Math.random(), 0, 3);
		}
	}

	public static boolean removeAroundPoint(Location loc, double radius) {
		for (Combustion combustion : instances.values()) {
			if (combustion.location.getWorld() == loc.getWorld()) {
				if (combustion.location.distance(loc) <= radius) {
					explode(combustion.getPlayer());
					combustion.remove();
					return true;
				}
			}
		}
		return false;
	}

	private void advanceLocation() {
		ParticleEffect.FIREWORKS_SPARK.display(location, (float) Math.random() / 2, (float) Math.random() / 2,
				(float) Math.random() / 2, 0, 5);
		ParticleEffect.FLAME.display(location, (float) Math.random() / 2, (float) Math.random() / 2, (float) Math.random() / 2,
				0, 2);
		// if (Methods.rand.nextInt(4) == 0) {
		FireMethods.playCombustionSound(location);
		// }
		location = location.add(direction.clone().multiply(speedfactor));
	}

	private void createExplosion(Location block, float power, boolean breakblocks) {
		block.getWorld().createExplosion(block.getX(), block.getY(), block.getZ(), (float) defaultpower, true, breakblocks);
		for (Entity entity : block.getWorld().getEntities()) {
			if (entity instanceof LivingEntity) {
				if (entity.getLocation().distance(block) < radius) { // They are close enough to the
																		// explosion.
					GeneralMethods.damageEntity(player, entity, damage, "Combustion");
					AirMethods.breakBreathbendingHold(entity);
				}
			}
		}
		remove();

	}

	public boolean progress() {
		if (!instances.containsKey(player)) {
			return false;
		}

		if (player.isDead() || !player.isOnline()) {
			remove();
			return false;
		}

		if (!GeneralMethods.canBend(player.getName(), "Combustion")) {
			remove();
			return false;
		}

		if (GeneralMethods.getBoundAbility(player) == null
				|| !GeneralMethods.getBoundAbility(player).equalsIgnoreCase("Combustion")) {
			remove();
			return false;
		}

		if (GeneralMethods.isRegionProtectedFromBuild(player, "Combustion", location)) {
			remove();
			return false;
		}

		speedfactor = speed * (ProjectKorra.time_step / 1000.);
		ticks++;
		if (ticks > maxticks) {
			remove();
			return false;
		}

		if (location.distance(origin) > range) {
			remove();
			return false;
		}

		Block block = location.getBlock();
		if (block != null) {
			if (block.getType() != Material.AIR && block.getType() != Material.WATER
					&& block.getType() != Material.STATIONARY_WATER) {
				createExplosion(block.getLocation(), power, breakblocks);
			}
		}

		for (Entity entity : location.getWorld().getEntities()) {
			if (entity instanceof LivingEntity) {
				if (entity.getLocation().distance(location) <= 2 && !entity.equals(player)) {
					createExplosion(location, power, breakblocks);
				}
			}
		}

		advanceLocation();
		return true;
	}

	public static void progressAll() {
		for (Combustion ability : instances.values()) {
			ability.progress();
		}
	}

	@Override
	public void reloadVariables() {
		chargeTime = config.get().getLong("Abilities.Fire.Combustion.ChargeTime");
		cooldown = config.get().getLong("Abilities.Fire.Combustion.Cooldown");

		speed = config.get().getDouble("Abilities.Fire.Combustion.Speed");
		defaultrange = config.get().getDouble("Abilities.Fire.Combustion.Range");
		defaultpower = config.get().getDouble("Abilities.Fire.Combustion.Power");
		breakblocks = config.get().getBoolean("Abilities.Fire.Combustion.BreakBlocks");
		radius = config.get().getDouble("Abilities.Fire.Combustion.Radius");
		defaultdamage = config.get().getDouble("Abilities.Fire.Combustion.Damage");
	}

	public void remove() {
		instances.remove(player);
	}

	public Player getPlayer() {
		return player;
	}
}
