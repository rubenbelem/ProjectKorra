package com.projectkorra.projectkorra.airbending;

import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.configuration.ConfigLoadable;
import com.projectkorra.projectkorra.util.Flight;

public class FlightAbility implements ConfigLoadable {
	
	public static ConcurrentHashMap<Player, FlightAbility> instances = new ConcurrentHashMap<>();
	
	private static ConcurrentHashMap<String, Integer> hits = new ConcurrentHashMap<String, Integer>();
	private static ConcurrentHashMap<String, Boolean> hovering = new ConcurrentHashMap<String, Boolean>();
	private Player player;
	private Flight flight;
	
	private static long cooldown = ProjectKorra.plugin.getConfig().getLong("Abilities.Air.Flight.Cooldown");

	public FlightAbility(Player player) {
		this.player = player;
		BendingPlayer bPlayer = GeneralMethods.getBendingPlayer(player.getName());
		if (bPlayer.isOnCooldown("Flight"))
			return;
		if (!AirMethods.canFly(player, true, false))
			return;
		if (flight == null)
			flight = new Flight(player);
		player.setAllowFlight(true);
		player.setVelocity(player.getEyeLocation().getDirection().normalize());
		bPlayer.addCooldown("Flight", cooldown);
		instances.put(player, this);
	}

	public static void addHit(Player player) {
		if (contains(player)) {
			if (hits.containsKey(player.getName())) {
				if (hits.get(player.getName()) >= 4) {
					hits.remove(player.getName());
					remove(player);
				}
			} else {
				hits.put(player.getName(), 1);
			}
		}
	}

	public static boolean contains(Player player) {
		return instances.containsKey(player);
	}

	public static boolean isHovering(Player player) {
		return hovering.containsKey(player.getName());
	}

	public static void remove(Player player) {
		if (contains(player))
			instances.get(player).remove();
	}

	public static void removeAll() {
		for (FlightAbility ability : instances.values()) {
			ability.remove();
		}
		hits.clear();
		hovering.clear();
	}

	public static void setHovering(Player player, boolean bool) {
		String playername = player.getName();

		if (bool) {
			if (!hovering.containsKey(playername)) {
				hovering.put(playername, true);
				player.setVelocity(new Vector(0, 0, 0));
			}
		} else {
			if (hovering.containsKey(playername)) {
				hovering.remove(playername);
			}
		}
	}

	public boolean progress() {
		if (!AirMethods.canFly(player, false, isHovering(player))) {
			remove(player);
			return false;
		}

		if (flight == null)
			flight = new Flight(player);

		if (isHovering(player)) {
			Vector vec = player.getVelocity().clone();
			vec.setY(0);
			player.setVelocity(vec);
		} else {
			player.setVelocity(player.getEyeLocation().getDirection().normalize());
		}
		return true;
	}

	public static void progressAll() {
		for (FlightAbility ability : instances.values()) {
			ability.progress();
		}
	}

	@Override
	public void reloadVariables() {
	}

	public void remove() {
		String name = player.getName();
		instances.remove(player);
		hits.remove(name);
		hovering.remove(name);
		if (flight != null)
			flight.revert();
	}

}
