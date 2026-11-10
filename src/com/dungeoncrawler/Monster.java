package com.dungeoncrawler;

public class Monster extends Entity {
    private final char symbol;
    private final String color;
    private final String name;
    private final double speed;
    private final int minDmg;
    private final int maxDmg;

    public Monster(int x, int y, char symbol, String color, String name, double speed, int minDmg, int maxDmg, int hp) {
        super(x, y, hp, hp);
        this.symbol = symbol;
        this.color = color;
        this.name = name;
        this.speed = speed;
        this.minDmg = minDmg;
        this.maxDmg = maxDmg;
    }

    @Override
    public String getEntityName() { return name; }

    public void moveTowards(int targetX, int targetY, DungeonMap map) {
        int dx = Integer.compare(targetX, x);
        int dy = Integer.compare(targetY, y);

        if (dx != 0 && map.isWalkable(this.x + dx, this.y) && !map.hasMonsterAt(this.x + dx, this.y)) {
            this.x += dx;
        } else if (dy != 0 && map.isWalkable(this.x, this.y + dy) && !map.hasMonsterAt(this.x, this.y + dy)) {
            this.y += dy;
        }
    }

    public char getSymbol() { return symbol; }
    public String getColor() { return color; }
    public String getName() { return name; }
    public double getSpeed() { return speed; }
    public int getMinDmg() { return minDmg; }
    public int getMaxDmg() { return maxDmg; }
}
