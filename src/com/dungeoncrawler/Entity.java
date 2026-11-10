package com.dungeoncrawler;

public abstract class Entity {
    protected int x, y;
    protected int hp, maxHp;

    public Entity(int x, int y, int hp, int maxHp) {
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.maxHp = maxHp;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }

    public void setPosition(int x, int y) { 
        this.x = x; 
        this.y = y; 
    }
    
    public void takeDamage(int amount) { 
        this.hp = Math.max(0, this.hp - amount); 
    }

    public abstract String getEntityName();
}
