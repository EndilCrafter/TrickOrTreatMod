package net.mehvahdjukaar.hauntedharvest;

import net.mehvahdjukaar.hauntedharvest.ai.HalloweenVillagerAI;
import net.mehvahdjukaar.hauntedharvest.blocks.ModCarvedPumpkinBlock;
import net.mehvahdjukaar.hauntedharvest.blocks.PumpkinType;
import net.mehvahdjukaar.hauntedharvest.configs.CommonConfigs;
import net.mehvahdjukaar.hauntedharvest.integration.CompatHandler;
import net.mehvahdjukaar.hauntedharvest.network.NetworkHandler;
import net.mehvahdjukaar.hauntedharvest.reg.ModCommands;
import net.mehvahdjukaar.hauntedharvest.reg.ModRegistry;
import net.mehvahdjukaar.hauntedharvest.reg.ModTabs;
import net.mehvahdjukaar.moonlight.api.misc.EventCalled;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.util.AnimalFoodHelper;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Author: MehVahdJukaar
 */
public class HauntedHarvest {

    public static final String MOD_ID = "hauntedharvest";

    public static ResourceLocation res(String name) {
        return new ResourceLocation(MOD_ID, name);
    }

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static SeasonManager seasonManager;

    public static void commonInit() {
        CommonConfigs.init();
        ModCommands.init();
        ModRegistry.init();
        ModTabs.init();
        CompatHandler.init();

        RegHelper.registerSimpleRecipeCondition(res("flag"), CommonConfigs::isEnabled);
        PlatHelper.addServerReloadListener(CustomCarvingsManager.RELOAD_INSTANCE, res("pumpkin_carvings"));
        //TODO: pillager outposts pumpkins
    }

    //needs to be fired after configs are loaded
    public static void commonSetup() {
        NetworkHandler.registerMessages();

        PumpkinType.setup();
        CompatHandler.setup();
        HalloweenVillagerAI.setup();

        ComposterBlock.COMPOSTABLES.put(ModRegistry.CARVED_PUMPKIN.get().asItem(), 0.65F);
        ComposterBlock.COMPOSTABLES.put(ModRegistry.KERNELS.get().asItem(), 0.3F);
        ComposterBlock.COMPOSTABLES.put(ModRegistry.COB_ITEM.get().asItem(), 0.5F);

        AnimalFoodHelper.addChickenFood(ModRegistry.KERNELS.get());
        AnimalFoodHelper.addParrotFood(ModRegistry.KERNELS.get());

        DispenseItemBehavior armorBehavior = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                this.setSuccess(ArmorItem.dispenseArmor(source, stack));
                return stack;
            }
        };
        DispenserBlock.registerBehavior(ModRegistry.PAPER_BAG.get(), armorBehavior);
    }

    public static SeasonManager getSeasonManager() {
        if (seasonManager == null) seasonManager = new SeasonManager();
        return seasonManager;
    }

    //TODO: add witches to villages using structure modifiers
    //TODO: give candy to players
    //TODO: fix when inventory is full
    //TODO: click on pumpkin with torch
    //custom carve sounds


    public static boolean isPlayerOnCooldown(LivingEntity self) {
        return false;
    }

    public static boolean isHalloweenSeason(Level level) {
        return seasonManager.isHalloween(level);
    }

    public static boolean isTrickOrTreatTime(Level level) {
        return seasonManager.isTrickOrTreatTime(level);
    }

    //refresh configs and tag stuff
    @EventCalled
    public static void onTagLoad() {
        HalloweenVillagerAI.refreshCandies();
    }

    @EventCalled
    public static InteractionResult onRightClickBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        Direction direction = hit.getDirection();
        var t = PumpkinType.getFromTorch(stack.getItem());
        if(t != null){
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if(state.is(Blocks.PUMPKIN)) {
                BlockState toPlace = t.withPropertiesOf(state);
                SoundType soundType = toPlace.getSoundType();
                level.playSound(player, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f);
                level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, toPlace));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.setBlockAndUpdate(pos, toPlace);
            }
            return InteractionResult.PASS;
        }
        if (direction == Direction.UP && HHPlatformStuff.isTopCarver(stack)) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.PUMPKIN)) {
                level.playSound(player, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!level.isClientSide) {
                    ItemEntity itemEntity = new ItemEntity(level,
                            pos.getX() + 0.5, pos.getY() + 1.15f, pos.getZ() + 0.5,
                            new ItemStack(Items.PUMPKIN_SEEDS, 4));

                    itemEntity.setDeltaMovement(level.random.nextDouble() * 0.02, 0.05 + level.random.nextDouble() * 0.02, level.random.nextDouble() * 0.02);
                    level.addFreshEntity(itemEntity);

                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger((ServerPlayer) player, pos, stack);
                    stack.hurtAndBreak(1, player, (l) -> l.broadcastBreakEvent(hand));
                    player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                    level.gameEvent(player, GameEvent.SHEAR, pos);
                    level.setBlock(pos, ModRegistry.CARVED_PUMPKIN.get().withPropertiesOf(state)
                            .setValue(ModCarvedPumpkinBlock.FACING, player.getDirection().getOpposite()), 11);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }


}
