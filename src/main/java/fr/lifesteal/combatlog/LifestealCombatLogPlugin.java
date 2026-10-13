package fr.lifesteal.combatlog;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Companion plugin for the "Lifesteal + Combat Log" datapack.
 * <p>
 * A vanilla datapack has NO way to react at the exact instant a player
 * disconnects (there is no "player quit" event in vanilla). The datapack
 * already works around this by punishing the player when they RECONNECT
 * (see combat/on_rejoin.mcfunction) - this plugin simply makes that
 * punishment happen INSTANTLY, the moment they log out, by hooking into
 * Paper's real PlayerQuitEvent.
 * <p>
 * Everything here reads/writes the SAME vanilla scoreboard objectives the
 * datapack uses ("hearts", "combat", "hc_transfer_ok", ...), so the two
 * systems stay perfectly in sync - you can run the datapack alone (fallback
 * behavior on rejoin) or with this plugin installed (instant behavior).
 */
public final class LifestealCombatLogPlugin extends JavaPlugin implements Listener {

    /** victimUUID <-> opponentUUID, updated on every PvP hit, used only to credit kills on combat-log. */
    private final Map<UUID, UUID> lastOpponent = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LifestealCombatLogGuard enabled - watching for combat-log disconnects.");
    }

    // ------------------------------------------------------------------
    // Track who is fighting whom (used only to credit the opponent later)
    // ------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        LivingEntity attacker = resolveAttacker(event);
        if (!(attacker instanceof Player attackerPlayer)) {
            return;
        }
        if (attackerPlayer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        lastOpponent.put(victim.getUniqueId(), attackerPlayer.getUniqueId());
        lastOpponent.put(attackerPlayer.getUniqueId(), victim.getUniqueId());
    }

    private LivingEntity resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof LivingEntity shooter) {
                return shooter;
            }
            return null;
        }
        if (event.getDamager() instanceof LivingEntity livingDamager) {
            return livingDamager;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The actual combat-log punishment, applied the instant they quit
    // ------------------------------------------------------------------
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        Objective combatObjective = board.getObjective("combat");
        if (combatObjective == null) {
            return; // datapack not loaded / objective missing - nothing we can safely do
        }

        boolean wasInCombat = combatObjective.getScore(player.getName()).getScore() > 0;
        if (!wasInCombat) {
            lastOpponent.remove(player.getUniqueId());
            return; // normal disconnect, nothing to punish
        }

        // 1) Drop everything on the ground right now, at the exact spot they were standing
        dropAndClearInventory(player);

        // 2) Clear their combat state so the datapack's own rejoin-check doesn't fire again
        combatObjective.getScore(player.getName()).setScore(0);
        player.removeScoreboardTag("hc_in_combat");

        // 3) Apply the Lifesteal heart loss (10 hearts = hard floor, never goes lower)
        Objective heartsObjective = board.getObjective("hearts");
        int hearts = heartsObjective != null ? heartsObjective.getScore(player.getName()).getScore() : 10;
        boolean transferOk = hearts > 10;

        if (transferOk && heartsObjective != null) {
            int newHearts = hearts - 1;
            heartsObjective.getScore(player.getName()).setScore(newHearts);
            syncMaxHealth(player, newHearts);
        }

        // 4) Credit whoever they were fighting, if that player is still online
        UUID opponentId = lastOpponent.get(player.getUniqueId());
        if (transferOk && opponentId != null) {
            Player opponent = Bukkit.getPlayer(opponentId);
            if (opponent != null && opponent.isOnline()) {
                Objective transferOkObjective = board.getObjective("hc_transfer_ok");
                if (transferOkObjective != null) {
                    transferOkObjective.getScore("$const").setScore(1);
                }
                // Delegate to the datapack's own function so the heart gain,
                // sound and chat message stay perfectly consistent with a
                // normal kill.
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "execute as " + opponent.getName() + " run function lifesteal:combat/killer_gain_heart"
                );
            }
        }

        lastOpponent.remove(player.getUniqueId());
    }

    private void dropAndClearInventory(Player player) {
        Location loc = player.getLocation();
        PlayerInventory inv = player.getInventory();

        for (ItemStack item : inv.getContents()) {
            dropIfPresent(loc, item);
        }
        for (ItemStack item : inv.getArmorContents()) {
            dropIfPresent(loc, item);
        }
        dropIfPresent(loc, inv.getItemInOffHand());

        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
    }

    private void dropIfPresent(Location loc, ItemStack item) {
        if (item != null && item.getType() != Material.AIR && loc.getWorld() != null) {
            loc.getWorld().dropItemNaturally(loc, item);
        }
    }

    /**
     * Keeps the player's real max-health attribute (and current health) in
     * sync with their new heart count, exactly like lifesteal:heart/apply
     * does in the datapack - done directly through the Bukkit API here
     * because the player object may no longer be reliably targetable by
     * vanilla commands at the exact instant they disconnect.
     */
    private void syncMaxHealth(Player player, int hearts) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double newMax = hearts * 2.0;
        maxHealth.setBaseValue(newMax);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
