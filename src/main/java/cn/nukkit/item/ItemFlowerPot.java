package cn.nukkit.item;

import cn.nukkit.block.Block;

/**
 * @author Snake1999
 * @since 2016/2/4
 */
public class ItemFlowerPot extends Item {

    public ItemFlowerPot() {
        this(0, 1);
    }

    public ItemFlowerPot(Integer meta) {
        this(meta, 1);
    }

    public ItemFlowerPot(Integer meta, int count) {
        super(FLOWER_POT, meta, count, "Flower Pot");
        this.block = Block.get(Block.FLOWER_POT);
    }
}
