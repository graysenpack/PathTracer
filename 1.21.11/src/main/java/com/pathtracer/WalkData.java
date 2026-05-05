package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Stores walk count and the last in-game day a player walked through a block position.
 */
@Environment(EnvType.CLIENT)
public class WalkData {

    private int count;
    private long lastGameDay;

    public WalkData(int count, long lastGameDay) {
        this.count = count;
        this.lastGameDay = lastGameDay;
    }

    public int getCount() {
        return count;
    }

    public long getLastGameDay() {
        return lastGameDay;
    }

    /**
     * Increment the walk count and update the last-walked game day.
     */
    public void increment(long currentGameDay) {
        this.count++;
        this.lastGameDay = currentGameDay;
    }

    /**
     * Returns true if this entry is older than maxAgeDays game days.
     */
    public boolean isExpired(long currentGameDay, long maxAgeDays) {
        return (currentGameDay - lastGameDay) > maxAgeDays;
    }
}
