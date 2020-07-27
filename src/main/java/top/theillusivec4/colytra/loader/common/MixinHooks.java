package top.theillusivec4.colytra.loader.common;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import top.theillusivec4.colytra.core.Colytra;
import top.theillusivec4.colytra.core.base.ColytraConfig.ColytraMode;
import top.theillusivec4.colytra.core.util.ColytraHooks;
import top.theillusivec4.colytra.core.util.ElytraTag;
import top.theillusivec4.colytra.loader.mixin.AnvilScreenHandlerAccessor;
import top.theillusivec4.colytra.loader.mixin.ExperienceOrbEntityAccessor;
import top.theillusivec4.colytra.loader.mixin.ForgingScreenHandlerAccessor;
import top.theillusivec4.colytra.loader.network.NetworkPackets;

public class MixinHooks {

  public static void syncConfig(PlayerEntity playerEntity) {
    ServerSidePacketRegistry.INSTANCE.sendToPlayer(playerEntity, NetworkPackets.SYNC_CONFIG,
        NetworkPackets.writeConfigPacket(new PacketByteBuf(Unpooled.buffer())));
  }

  public static void updateColytra(PlayerEntity playerEntity) {
    ColytraHooks.updateColytra(playerEntity);
  }

  public static void appendColytraTooltip(ItemStack stack, List<Text> tooltip) {
    ColytraHooks.appendColytraTooltip(stack, tooltip);
  }

  public static boolean checkColytraRepair(AnvilScreenHandler anvil) {

    if (Colytra.getConfig().getColytraMode() != ColytraMode.NORMAL) {
      return false;
    }
    ForgingScreenHandlerAccessor forgingAccessor = (ForgingScreenHandlerAccessor) anvil;
    AnvilScreenHandlerAccessor anvilAccessor = (AnvilScreenHandlerAccessor) anvil;
    ItemStack chestStack = forgingAccessor.getInput().getStack(0);

    if (chestStack.isEmpty()) {
      return false;
    }
    ItemStack stack = ElytraTag.getElytra(chestStack);

    if (stack.isEmpty()) {
      return false;
    }
    ItemStack right = ((ForgingScreenHandlerAccessor) anvil).getInput().getStack(1);
    int toRepair = stack.getDamage();

    if (right.getItem() != Items.PHANTOM_MEMBRANE || toRepair == 0) {
      return false;
    }
    int membraneToUse = 0;

    while (toRepair > 0) {
      toRepair -= 108;
      membraneToUse++;
    }
    membraneToUse = Math.min(membraneToUse, right.getCount());
    int newDamage = Math.max(stack.getDamage() - membraneToUse * 108, 0);

    ItemStack output = chestStack.copy();
    ItemStack outputElytra = stack.copy();
    outputElytra.setDamage(newDamage);
    outputElytra.setRepairCost(stack.getRepairCost() * 2 + 1);
    ElytraTag.setElytra(output, outputElytra);
    int xpCost = membraneToUse + chestStack.getRepairCost() + right.getRepairCost();

    if (StringUtils.isBlank(anvilAccessor.getNewItemName())) {
      if (stack.hasCustomName()) {
        xpCost += 1;
        stack.removeCustomName();
      }
    } else if (!anvilAccessor.getNewItemName().equals(stack.getName().getString())) {
      xpCost += 1;
      output.setCustomName(new LiteralText(anvilAccessor.getNewItemName()));
    }
    anvilAccessor.setRepairItemUsage(membraneToUse);
    anvilAccessor.getLevelCost().set(xpCost);
    forgingAccessor.getOutput().setStack(0, output);
    anvil.sendContentUpdates();
    return true;
  }

  public static boolean checkColytraMending(PlayerEntity playerEntity, ExperienceOrbEntity orb) {

    if (Colytra.getConfig().getColytraMode() != ColytraMode.NORMAL) {
      return false;
    }
    ItemStack chestStack = playerEntity.getEquippedStack(EquipmentSlot.CHEST);
    ItemStack elytraStack = ElytraTag.getElytra(chestStack);

    if (!elytraStack.isEmpty() && EnchantmentHelper.getLevel(Enchantments.MENDING, elytraStack) > 0
        && elytraStack.isDamaged()) {
      playerEntity.experiencePickUpDelay = 2;
      playerEntity.sendPickup(orb, 1);
      int toRepair = Math.min(orb.getExperienceAmount() * 2, elytraStack.getDamage());
      ExperienceOrbEntityAccessor accessedOrb = (ExperienceOrbEntityAccessor) orb;
      accessedOrb.setAmount(accessedOrb.getAmount() - (toRepair / 2));
      elytraStack.setDamage(elytraStack.getDamage() - toRepair);

      if (orb.getExperienceAmount() > 0) {
        playerEntity.addExperience(orb.getExperienceAmount());
      }
      orb.remove();
      return true;
    }
    return false;
  }
}
