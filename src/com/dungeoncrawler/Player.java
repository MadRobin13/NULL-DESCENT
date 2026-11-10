package com.dungeoncrawler;

import com.dungeoncrawler.engine.EventManager;
import java.util.ArrayList;
import java.util.List;

public class Player extends Entity {
    private int score;
    private ArrayList<Item> inventory;
    private boolean firewallPatchActive;

    public Player(int x, int y) {
        super(x, y, 100, 100);
        this.score = 0;
        this.inventory = new ArrayList<>();
        this.firewallPatchActive = false;
    }

    @Override
    public String getEntityName() { return "Hero"; }

    public int getScore() { return score; }
    public void addScore(int amount) { this.score += amount; }
    public void setScore(int score) { this.score = score; }
    
    // Allow state injection during load
    public void setHp(int hp) { this.hp = hp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    
    public void heal(int amount) { this.hp = Math.min(this.maxHp, this.hp + amount); }
    
    public boolean isFirewallPatchActive() { return firewallPatchActive; }
    public void setFirewallPatchActive(boolean active) { this.firewallPatchActive = active; }
    
    public void reset() { 
        this.hp = this.maxHp; 
        this.score = 0; 
        this.inventory.clear();
        this.firewallPatchActive = false;
    }

    public List<Item> getInventory() { return inventory; }
    public void addItem(Item item) { this.inventory.add(item); }
    public void removeItem(Item item) { this.inventory.remove(item); }

    public void sortInventory(String criteria) {
        int n = inventory.size();
        for (int i = 0; i < n - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < n; j++) {
                boolean swap = false;
                Item item1 = inventory.get(j);
                Item item2 = inventory.get(minIdx);
                
                switch (criteria.toLowerCase()) {
                    case "name":
                        if (item1.compareTo(item2) < 0) swap = true; 
                        break;
                    case "value":
                        if (item1.getValue() < item2.getValue()) swap = true; 
                        break;
                }
                if (swap) {
                    minIdx = j;
                }
            }
            if (minIdx != i) {
                Item temp = inventory.get(minIdx);
                inventory.set(minIdx, inventory.get(i));
                inventory.set(i, temp);
            }
        }
    }

    public boolean move(int dx, int dy, DungeonMap map, EventManager events) {
        int newX = x + dx;
        int newY = y + dy;

        if (map.hasMonsterAt(newX, newY)) {
            Monster target = map.getMonsterAt(newX, newY);
            int damage = 15 + (int)(Math.random() * 10); 
            target.takeDamage(damage);
            
            if (target.getHp() <= 0) {
                events.publishEvent("Deleted " + target.getName() + "! (+50 Score)", EventManager.EventType.COMBAT);
                score += 50;
                map.removeMonster(target);
            } else {
                events.publishEvent("Struck " + target.getName() + " for " + damage + " dmg!", EventManager.EventType.COMBAT);
            }
            return true; 
        }

        if (map.isWalkable(newX, newY)) {
            this.x = newX;
            this.y = newY;
            map.triggerTile(this, events); 
            return true; 
        }

        return false; 
    }
}
