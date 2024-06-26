package cn.nukkit.item.enchantment.trident;

import cn.nukkit.api.DeprecationDetails;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

public abstract class EnchantmentTrident extends Enchantment {
    protected EnchantmentTrident(int id, String name, Rarity rarity) {
        super(id, name, rarity, EnchantmentType.TRIDENT);
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return 50;
    }
}
