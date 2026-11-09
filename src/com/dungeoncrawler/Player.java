package com.dungeoncrawler;
import com.dungeoncrawler.engine.EventManager;

public class Player {
    private int x, y;
    private int hp, maxHp;
    private int score;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;
        this.maxHp = 100;
        this.hp = maxHp;
        this.score = 0;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getScore() { return score; }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void addScore(int amount) { this.score += amount; }
    public void takeDamage(int amount) { this.hp -= amount; }
    public void heal(int amount) { this.hp = Math.min(this.maxHp, this.hp + amount); }
    public void reset() { this.hp = this.maxHp; this.score = 0; }

    public boolean move(int dx, int dy, DungeonMap map, EventManager events) {
        int newX = x + dx;
        int newY = y + dy;

        // 1. Check for Combat FIRST
        if (map.hasMonsterAt(newX, newY)) {
            Monster target = map.getMonsterAt(newX, newY);
            int damage = 15 + (int)(Math.random() * 10); // Player deals 15-24 dmg
            target.takeDamage(damage);
            
            if (target.getHp() <= 0) {
                events.publishEvent("Deleted " + target.getName() + "! (+50 Score)", EventManager.EventType.COMBAT);
                score += 50;
                map.removeMonster(target);
            } else {
                events.publishEvent("Struck " + target.getName() + " for " + damage + " dmg!", EventManager.EventType.COMBAT);
            }
            return true; // Action taken
        }

        // 2. Check for Movement
        if (map.isWalkable(newX, newY)) {
            this.x = newX;
            this.y = newY;
            map.triggerTile(this, events); // Triggers items/staircase discovery
            return true; // Action taken
        }

        return false; // Hit a wall, action not taken
    }
}