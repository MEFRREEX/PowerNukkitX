package cn.nukkit.item;

public class ItemRabbit extends ItemFood {
    public ItemRabbit() {
        this(0, 1);
    }

    public ItemRabbit(Integer meta) {
        this(meta, 1);
    }

    public ItemRabbit(Integer meta, int count) {
        super(RABBIT, meta, count, "Raw Rabbit");
    }

    @Override
    public int getFoodRestore() {
        return 3;
    }

    @Override
    public float getSaturationRestore() {
        return 1.8F;
    }
}