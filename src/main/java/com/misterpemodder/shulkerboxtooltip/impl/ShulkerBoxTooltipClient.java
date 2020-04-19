package com.misterpemodder.shulkerboxtooltip.impl;

import java.util.List;
import javax.annotation.Nullable;
import com.misterpemodder.shulkerboxtooltip.api.PreviewContext;
import com.misterpemodder.shulkerboxtooltip.api.PreviewType;
import com.misterpemodder.shulkerboxtooltip.api.ShulkerBoxTooltipApi;
import com.misterpemodder.shulkerboxtooltip.api.provider.PreviewProvider;
import com.misterpemodder.shulkerboxtooltip.api.renderer.PreviewRenderer;
import com.misterpemodder.shulkerboxtooltip.impl.config.Configuration.ShulkerBoxTooltipType;
import com.misterpemodder.shulkerboxtooltip.impl.hook.ShulkerPreviewPosGetter;
import com.misterpemodder.shulkerboxtooltip.impl.network.server.S2CPacketTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public final class ShulkerBoxTooltipClient implements ClientModInitializer {
  private static ItemStack previousStack = null;
  private static MinecraftClient client;
  private static boolean wasPreviewAccessed = false;

  @Override
  public void onInitializeClient() {
    client = MinecraftClient.getInstance();
    if (ShulkerBoxTooltip.config.main.serverIntegration)
      S2CPacketTypes.register();
  }

  public static boolean shouldDisplayPreview() {
    return ShulkerBoxTooltip.config.main.alwaysOn || Screen.hasShiftDown();
  }

  @Nullable
  public static Text getTooltipHint(PreviewContext context, PreviewProvider provider) {
    boolean shouldDisplay = shouldDisplayPreview();

    if (!ShulkerBoxTooltip.config.main.enablePreview || !provider.shouldDisplay(context)
        || (shouldDisplay && Screen.hasAltDown()))
      return null;

    // At this point, SHIFT may be pressed but not ALT.
    boolean fullPreviewAvailable = provider.isFullPreviewAvailable(context);

    if (!fullPreviewAvailable && shouldDisplay)
      return null;

    String keyHint =
        shouldDisplay ? (ShulkerBoxTooltip.config.main.alwaysOn ? "Alt" : "Alt+Shift") : "Shift";
    String contentHint;

    if (ShulkerBoxTooltipApi.getCurrentPreviewType(fullPreviewAvailable) == PreviewType.NO_PREVIEW)
      contentHint =
          ShulkerBoxTooltip.config.main.swapModes ? provider.getFullTooltipHintLangKey(context)
              : provider.getTooltipHintLangKey(context);
    else
      contentHint =
          ShulkerBoxTooltip.config.main.swapModes ? provider.getTooltipHintLangKey(context)
              : provider.getFullTooltipHintLangKey(context);
    return new LiteralText(keyHint + ": ").setStyle(new Style().setColor(Formatting.GOLD))
        .append(new TranslatableText(contentHint).setStyle(new Style().setColor(Formatting.WHITE)));
  }

  public static void drawIfPreviewAvailable(Screen screen, ItemStack stack) {
    PreviewContext context = PreviewContext.of(stack, client.player);

    if (ShulkerBoxTooltipApi.isPreviewAvailable(context))
      drawShulkerBoxPreview(screen, ShulkerBoxTooltipApi.getPreviewProviderForStack(stack), context,
          stack);
  }

  private static void drawShulkerBoxPreview(Screen screen, PreviewProvider provider,
      PreviewContext context, ItemStack stack) {
    PreviewRenderer renderer = provider.getRenderer();

    if (renderer == null)
      renderer = PreviewRenderer.getDefaultRendererInstance();
    renderer.setPreview(context, provider);
    renderer.setPreviewType(
        ShulkerBoxTooltipApi.getCurrentPreviewType(provider.isFullPreviewAvailable(context)));

    int x = Math.min(((ShulkerPreviewPosGetter) screen).shulkerboxtooltip$getStartX() - 1,
        screen.width - renderer.getWidth());
    int y = ((ShulkerPreviewPosGetter) screen).shulkerboxtooltip$getBottomY() + 1;
    int h = renderer.getHeight();

    if (ShulkerBoxTooltip.config.main.lockPreview || y + h > screen.height)
      y = ((ShulkerPreviewPosGetter) screen).shulkerboxtooltip$getTopY() - h;
    renderer.draw(x, y);
  }

  public static void modifyStackTooltip(ItemStack stack, List<Text> tooltip) {
    PreviewContext context = PreviewContext.of(stack, client.player);
    PreviewProvider provider = ShulkerBoxTooltipApi.getPreviewProviderForStack(stack);

    if (provider == null)
      return;
    if (previousStack == null || !ItemStack.areEqual(stack, previousStack))
      wasPreviewAccessed = false;
    previousStack = stack;

    if (!wasPreviewAccessed)
      provider.onInventoryAccessStart(context);
    wasPreviewAccessed = true;

    if (provider.showTooltipHints(context)) {
      if (ShulkerBoxTooltip.config.main.tooltipType == ShulkerBoxTooltipType.MOD)
        tooltip.addAll(provider.addTooltip(context));
      if (ShulkerBoxTooltip.config.main.showKeyHints) {
        Text hint = ShulkerBoxTooltipClient.getTooltipHint(context, provider);

        if (hint != null)
          tooltip.add(hint);
      }
    }
  }
}
