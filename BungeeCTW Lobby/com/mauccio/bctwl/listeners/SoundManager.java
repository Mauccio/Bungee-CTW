package com.mauccio.bctwl.listeners;

import com.mauccio.bctwl.CTWLobby;
import org.bukkit.Sound;

public class SoundManager {

    private final CTWLobby ctwLobby;
    private Sound errorSound;
    private Sound alertSound;
    private Sound tipSound;
    private Sound guiSound;
    private Sound yourStatsSound;

    public SoundManager(CTWLobby ctwLobby) {
        this.ctwLobby = ctwLobby;
    }

    public void loadSounds() {
        errorSound = Sound.valueOf(ctwLobby.getConfig().getString("sounds.error", "ENDERDRAGON_HIT"));
        alertSound = Sound.valueOf(ctwLobby.getConfig().getString("sounds.alert", "NOTE_PLING"));
        tipSound = Sound.valueOf(ctwLobby.getConfig().getString("sounds.tip", "ITEM_PICKUP"));
        guiSound = Sound.valueOf(ctwLobby.getConfig().getString("sounds.gui", "CHEST_OPEN"));
        yourStatsSound = Sound.valueOf(ctwLobby.getConfig().getString("sounds.your-stats", "ORB_PICKUP"));
    }

    public Sound getErrorSound() {
        return errorSound;
    }

    public Sound getAlertSound() {
        return alertSound;
    }

    public Sound getTipSound() {
        return tipSound;
    }

    public Sound getGuiSound() {
        return guiSound;
    }
    
    public Sound getYourStatsSound() {
        return yourStatsSound;
    }


    public void playErrorSound(org.bukkit.entity.Player player) {
        if (ctwLobby.getConfig().getBoolean("sounds.enabled", true)) {
            try {
                player.playSound(player.getLocation(), getErrorSound(), 1.0f, 1.0f);
            } catch (NullPointerException e) {
                ctwLobby.getLogger().warning("Error sound is not configured properly.");
            }
        }
    }

    public void playAlertSound(org.bukkit.entity.Player player) {
        if (ctwLobby.getConfig().getBoolean("sounds.enabled", true)) {
            try {
                player.playSound(player.getLocation(), getAlertSound(), 1.0f, 1.0f);
            } catch (NullPointerException e) {
                ctwLobby.getLogger().warning("Alert sound is not configured properly.");
            }
        }
    }

    public void playTipSound(org.bukkit.entity.Player player) {
        if (ctwLobby.getConfig().getBoolean("sounds.enabled", true)) {
            try {
                player.playSound(player.getLocation(), getTipSound(), 1.0f, 1.0f);
            } catch (NullPointerException e) {
                ctwLobby.getLogger().warning("Tip sound is not configured properly.");
            }
        }
    }
    
    public void playYourStatsSound(org.bukkit.entity.Player player) {
        if (ctwLobby.getConfig().getBoolean("sounds.enabled", true)) {
            try {
                player.playSound(player.getLocation(), getYourStatsSound(), 1.0f, 1.0f);
            } catch (NullPointerException e) {
                ctwLobby.getLogger().warning("Your stats sound is not configured properly.");
            }
        }
    }

    public void playGuiSound(org.bukkit.entity.Player player) {
        if (ctwLobby.getConfig().getBoolean("sounds.enabled", true)) {
            try {
                player.playSound(player.getLocation(), getGuiSound(), 1.0f, 1.0f);
            } catch (NullPointerException e) {
                ctwLobby.getLogger().warning("Room GUI sound is not configured properly.");
            }
        }
    }
}
