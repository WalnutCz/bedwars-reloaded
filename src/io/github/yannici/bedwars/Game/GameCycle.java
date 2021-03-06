package io.github.yannici.bedwars.Game;

import java.util.Collection;

import io.github.yannici.bedwars.Main;
import io.github.yannici.bedwars.Utils;
import io.github.yannici.bedwars.Events.BedwarsGameOverEvent;
import io.github.yannici.bedwars.Events.BedwarsPlayerKilledEvent;
import io.github.yannici.bedwars.Statistics.PlayerStatistic;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.google.common.collect.ImmutableMap;

public abstract class GameCycle {

	private Game game = null;
	private boolean endGameRunning = false;

	public GameCycle(Game game) {
		this.game = game;
	}

	public Game getGame() {
		return game;
	}

	public abstract void onGameStart();

	public abstract void onGameEnds();

	public abstract void onPlayerLeave(Player player);

	public abstract void onGameLoaded();

	public abstract boolean onPlayerJoins(Player player);

	public abstract void onGameOver(GameOverTask task);

	private void runGameOver(Team winner) {
		BedwarsGameOverEvent overEvent = new BedwarsGameOverEvent(
				this.getGame(), winner);
		Main.getInstance().getServer().getPluginManager().callEvent(overEvent);

		if (overEvent.isCancelled()) {
			return;
		}

		this.getGame().stopWorkers();
		this.setEndGameRunning(true);
		int delay = Main.getInstance().getConfig().getInt("gameoverdelay"); // configurable
																			// delay
		if (Main.getInstance().statisticsEnabled()) {
			if (winner != null) {
				for (Player player : winner.getPlayers()) {
					PlayerStatistic statistic = Main.getInstance()
							.getPlayerStatisticManager().getStatistic(player);
					statistic.setWins(statistic.getWins() + 1);
					statistic.setScore(statistic.getScore()
							+ Main.getInstance().getIntConfig(
									"statistics.scores.win", 50));
				}
			}
			
			for(Player player : this.game.getPlayers()) {
			    PlayerStatistic statistic = Main.getInstance()
                        .getPlayerStatisticManager().getStatistic(player);
			    statistic.store();
			}
		}

		GameOverTask gameOver = new GameOverTask(this, delay, winner);
		gameOver.runTaskTimer(Main.getInstance(), 0L, 20L);
	}

	public void checkGameOver() {
		if (!Main.getInstance().isEnabled()) {
			return;
		}

		Team winner = this.getGame().isOver();
		if (winner != null) {
			if (this.isEndGameRunning() == false) {
				this.runGameOver(winner);
			}
		} else {
			if ((this.getGame().getTeamPlayers().size() == 0 || this.getGame()
					.isOverSet()) && this.isEndGameRunning() == false) {
				this.runGameOver(null);
			}
		}
	}

	public void onPlayerRespawn(PlayerRespawnEvent pre, Player player) {
		Team team = Game.getPlayerTeam(player, this.getGame());

		// reset damager
		this.getGame().setPlayerDamager(player, null);

		if (team == null) {
			if (this.getGame().isSpectator(player)) {
				Collection<Team> teams = this.getGame().getTeams().values();
				pre.setRespawnLocation(((Team) teams.toArray()[Utils.randInt(0,
						teams.size() - 1)]).getSpawnLocation());
			}
			return;
		}

		if (team.isDead()) {
			PlayerStorage storage = this.getGame().getPlayerStorage(player);
			
			if(Main.getInstance().statisticsEnabled()) {
				PlayerStatistic statistic = Main.getInstance().getPlayerStatisticManager().getStatistic(player);
				statistic.setLoses(statistic.getLoses()+1);
			}

			if (Main.getInstance().spectationEnabled()) {
				if (storage != null) {
					if (storage.getLeft() != null) {
						pre.setRespawnLocation(team.getSpawnLocation());
					}
				}

				this.getGame().toSpectator(player);
			} else {
				if(this.game.getCycle() instanceof BungeeGameCycle) {
					this.getGame().playerLeave(player);
					return;
				}
				
				if (!Main.getInstance().toMainLobby()) {
					if (storage != null) {
						if (storage.getLeft() != null) {
							pre.setRespawnLocation(storage.getLeft());
						}
					}
				} else {
					if (this.getGame().getMainLobby() != null) {
						pre.setRespawnLocation(this.getGame().getMainLobby());
					} else {
						if (storage != null) {
							if (storage.getLeft() != null) {
								pre.setRespawnLocation(storage.getLeft());
							}
						}
					}
				}

				this.getGame().playerLeave(player);
			}

		} else {
			if (Main.getInstance().getRespawnProtectionTime() > 0) {
				RespawnProtectionRunnable protection = this.getGame()
						.addProtection(player);
				protection.runProtection();
			}
			pre.setRespawnLocation(team.getSpawnLocation());
		}
	}

	public void onPlayerDies(Player player, Player killer) {
		if(this.isEndGameRunning()) {
			return;
		}
		
		BedwarsPlayerKilledEvent killedEvent = new BedwarsPlayerKilledEvent(
				this.getGame(), player, killer);
		Main.getInstance().getServer().getPluginManager()
				.callEvent(killedEvent);

		PlayerStatistic diePlayer = null;
		PlayerStatistic killerPlayer = null;

		if (Main.getInstance().statisticsEnabled()) {
			diePlayer = Main.getInstance().getPlayerStatisticManager()
					.getStatistic(player);
			killerPlayer = Main.getInstance().getPlayerStatisticManager()
					.getStatistic(player);

			diePlayer.setDeaths(diePlayer.getDeaths() + 1);
			diePlayer.setScore(diePlayer.getScore()
					+ Main.getInstance().getIntConfig("statistics.scores.die",
							0));
		}

		Team deathTeam = Game.getPlayerTeam(player, this.getGame());
		if (killer == null) {
			this.getGame()
					.broadcast(
							ChatColor.GOLD
									+ Main._l("ingame.player.died",
											ImmutableMap.of("player", Game
													.getPlayerWithTeamString(
															player, deathTeam,
															ChatColor.GOLD))));

			if (killerPlayer != null && Main.getInstance().statisticsEnabled()) {
				killerPlayer.setKills(killerPlayer.getKills() + 1);
				killerPlayer.setScore(killerPlayer.getScore()
						+ Main.getInstance().getIntConfig(
								"statistics.scores.kill", 10));
			}

			this.checkGameOver();
			return;
		}

		Team killerTeam = Game.getPlayerTeam(killer, this.getGame());
		if (killerTeam == null) {
			this.getGame()
					.broadcast(
							ChatColor.GOLD
									+ Main._l("ingame.player.died",
											ImmutableMap.of("player", Game
													.getPlayerWithTeamString(
															player, deathTeam,
															ChatColor.GOLD))));
			this.checkGameOver();
			return;
		}

		this.getGame().broadcast(
				ChatColor.GOLD
						+ Main._l("ingame.player.killed", ImmutableMap.of(
								"killer", Game.getPlayerWithTeamString(killer,
										killerTeam, ChatColor.GOLD), "player",
								Game.getPlayerWithTeamString(player, deathTeam,
										ChatColor.GOLD))));

		this.checkGameOver();
	}

	public void setEndGameRunning(boolean running) {
		this.endGameRunning = running;
	}

	public boolean isEndGameRunning() {
		return this.endGameRunning;
	}

}
