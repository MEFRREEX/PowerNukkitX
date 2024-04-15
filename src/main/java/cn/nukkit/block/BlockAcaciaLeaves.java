package cn.nukkit.block;

import cn.nukkit.block.property.CommonBlockProperties;
import cn.nukkit.block.property.enums.SaplingType;
import cn.nukkit.block.property.enums.WoodType;
import cn.nukkit.item.Item;
import org.jetbrains.annotations.NotNull;

import static cn.nukkit.block.property.CommonBlockProperties.SAPLING_TYPE;

public class BlockAcaciaLeaves extends BlockLeaves {
    public static final BlockProperties PROPERTIES = new BlockProperties(ACACIA_LEAVES, CommonBlockProperties.PERSISTENT_BIT, CommonBlockProperties.UPDATE_BIT);

    @Override
    @NotNull
    public BlockProperties getProperties() {
        return PROPERTIES;
    }

    public BlockAcaciaLeaves(BlockState blockstate) {
        super(blockstate);
    }

    @Override
    public WoodType getType() {
        return WoodType.ACACIA;
    }

    @Override
    public Item getSapling() {
        return BlockSapling.PROPERTIES.getBlockState(SAPLING_TYPE, SaplingType.ACACIA).toItem();
    }
}