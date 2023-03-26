package net.satisfy.candlelight.food;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class CandlelightFood extends Item {

    public static final String STORED_EFFECTS_KEY = "StoredEffects";

    public CandlelightFood(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            List<Pair<StatusEffectInstance, Float>> effects = getEffects(stack);
            for (Pair<StatusEffectInstance, Float> effect : effects) {
                if (effect.getFirst() != null && world.random.nextFloat() < effect.getSecond()) {
                    user.addStatusEffect(new StatusEffectInstance(effect.getFirst()));
                }
            }
        }
        return super.finishUsing(stack, world, user);
    }

    public static void addEffect(ItemStack stack, Pair<StatusEffectInstance, Float> effect) {
        NbtList nbtList = getEffectNbt(stack);
        boolean bl = true;
        int id = StatusEffect.getRawId(effect.getFirst().getEffectType());

        for(int i = 0; i < nbtList.size(); ++i) {
            NbtCompound nbtCompound = nbtList.getCompound(i);
            Identifier identifier2 = Identifier.tryParse(nbtCompound.getString("id"));
            if (identifier2 != null && identifier2.equals(id)) {
                bl = false;
                break;
            }
        }

        if (bl) {
            nbtList.add(createNbt((short)id, effect.getFirst().getDuration(), effect.getFirst().getAmplifier() , effect.getSecond()));
        }

        stack.getOrCreateNbt().put(STORED_EFFECTS_KEY, nbtList);
    }

    private static NbtList getEffectNbt(ItemStack stack) {
        NbtCompound nbtCompound = stack.getNbt();
        return nbtCompound != null ? nbtCompound.getList(STORED_EFFECTS_KEY, 10) : new NbtList();
    }

    private static NbtCompound createNbt(short id, int duration, int amplifier, float chance) {
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putShort("id", id);
        nbtCompound.putInt("duration", duration);
        nbtCompound.putInt("amplifier", amplifier);
        nbtCompound.putFloat("chance", chance);
        return nbtCompound;
    }

    public static List<Pair<StatusEffectInstance, Float>> getEffects(ItemStack stack) {
        if (stack.getItem() instanceof CandlelightFood) {
            return fromNbt(getEffectNbt(stack));
        }
        FoodComponent foodComponent = stack.getItem().getFoodComponent();
        assert foodComponent != null;
        return foodComponent.getStatusEffects();
    }

    public static List<Pair<StatusEffectInstance, Float>> fromNbt(NbtList list) {
        List<Pair<StatusEffectInstance, Float>> effects = Lists.newArrayList();
        for(int i = 0; i < list.size(); ++i) {
            NbtCompound nbtCompound = list.getCompound(i);
            StatusEffect effect = StatusEffect.byRawId(nbtCompound.getShort("id"));
            assert effect != null;
            effects.add(new Pair<>(new StatusEffectInstance(effect, nbtCompound.getInt("duration"), nbtCompound.getInt("amplifier")), nbtCompound.getFloat("chance")));
        }

        return effects;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        List<Pair<StatusEffectInstance, Float>> effects = getEffects(stack);
        List<Pair<EntityAttribute, EntityAttributeModifier>> list3 = Lists.newArrayList();
        if (effects.isEmpty()) {
            tooltip.add(Text.translatable("effect.none").formatted(Formatting.GRAY));
        } else {
            for (Pair<StatusEffectInstance, Float> statusEffectInstance : effects) {
                MutableText mutableText = Text.translatable(statusEffectInstance.getFirst().getTranslationKey());
                StatusEffect statusEffect = statusEffectInstance.getFirst().getEffectType();
                Map<EntityAttribute, EntityAttributeModifier> map = statusEffect.getAttributeModifiers();
                if (!map.isEmpty()) {
                    for (Map.Entry<EntityAttribute, EntityAttributeModifier> entry : map.entrySet()) {
                        EntityAttributeModifier entityAttributeModifier = entry.getValue();
                        EntityAttributeModifier entityAttributeModifier2 = new EntityAttributeModifier(
                                entityAttributeModifier.getName(),
                                statusEffect.adjustModifierAmount(statusEffectInstance.getFirst().getAmplifier(), entityAttributeModifier),
                                entityAttributeModifier.getOperation()
                        );
                        list3.add(new Pair<>(entry.getKey(), entityAttributeModifier2));
                    }
                }

                if (statusEffectInstance.getFirst().getDuration() > 20) {
                    mutableText = Text.translatable(
                            "potion.withDuration",
                            mutableText, StatusEffectUtil.durationToString(statusEffectInstance.getFirst(), statusEffectInstance.getSecond()));
                }

                tooltip.add(mutableText.formatted(statusEffect.getCategory().getFormatting()));
            }
        }
    }
}
