package com.dungeoncrawler;

public class Monster {
    private int x, y;
    private int hp;
    private final char symbol;
    private final String name;
    private final int minDmg;
    private final int maxDmg;
    private final double speed; // Chance to act per turn

    public Monster(int x, int y, char symbol, String name, int hp, int minDmg, int maxDmg, double speed) {
        this.x = x;
        this.y = y;
        this.symbol = symbol;
        this.name = name;
        this.hp = hp;
        this.minDmg = minDmg;
        this.maxDmg = maxDmg;
        this.speed = speed;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public char getSymbol() { return symbol; }
    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMinDmg() { return minDmg; }
    public int getMaxDmg() { return maxDmg; }
    public double getSpeed() { return speed; }

    public void takeDamage(int amount) {
        this.hp -= amount;
    }

    public void moveTowards(int targetX, int targetY, DungeonMap map) {
        int dx = Integer.compare(targetX, this.x);
        int dy = Integer.compare(targetY, this.y);

        if (dx != 0 && map.isWalkable(this.x + dx, this.y) && !map.hasMonsterAt(this.x + dx, this.y)) {
            this.x += dx;
        } else if (dy != 0 && map.isWalkable(this.x, this.y + dy) && !map.hasMonsterAt(this.x, this.y + dy)) {
            this.y += dy;
        }
    }
}