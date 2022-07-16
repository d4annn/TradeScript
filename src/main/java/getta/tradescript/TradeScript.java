package getta.tradescript;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import getta.tradescript.mixins.IMixinMerchantScreen;
import getta.tradescript.mixins.IMixinScreenWithHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.io.*;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;

public class TradeScript implements ClientModInitializer {

    public static final KeyBinding LUCIANO = new KeyBinding("toggle", InputUtil.Type.KEYSYM, -1, "Trade Script");
    public static final Identifier FONT = new Identifier("tradescript", "font");

    public static boolean on = false;
    public static Entity targetVillager;

    public static class Options {

        public int slot = 0;
        public boolean drop = true;
        public BlockPos pos = new BlockPos(0, 0, 0);

        public Options() {
        }
    }

    public static Options options;

    public boolean last = false;
    public static BlockPos pos = null;
    public static BlockPos pos1 = null;

    public static boolean guiOpened;
    public static boolean goThrowGateWayPart1OmgSoEpic;
    public static boolean traded;
    public static boolean goThrowGateWayPart2OmgSoEpicWhenSexPart2;
    public static boolean once = false;

    static int timer = 0;

    private static final File CONFIG_FILE = new File(getConfigDirectory().getPath() + "\\TradeScript.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static File getConfigDirectory() {

        return new File(MinecraftClient.getInstance().runDirectory, "config");
    }

    public void loadConfig() {
        checkConfig();
        try {
            BufferedReader br = new BufferedReader(new FileReader(CONFIG_FILE));
            Options config = GSON.fromJson(br, Options.class);
            br.close();
            if (null == config) {
                saveConfig();
            } else {
                options = config;
            }
        } catch (Exception e) {
            saveConfig();
        }
    }

    public void saveConfig() {
        checkConfig();
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(CONFIG_FILE));
            GSON.toJson(options, bw);
            bw.flush();
        } catch (Exception e) {
        }
    }

    public void checkConfig() {
        try {
            if (CONFIG_FILE.createNewFile()) {
                saveConfig();
            }
        } catch (IOException e) {
        }
    }

    @Override
    public void onInitializeClient() {
        options = new Options();
        loadConfig();
        saveConfig();
        KeyBindingHelper.registerKeyBinding(LUCIANO);
        ClientCommandManager.DISPATCHER.register(literal("tradescript")
                .then(literal("options").executes(context -> {
                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of("Trade slot: " + options.slot));
                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of("Trade drop: " + options.drop));
                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.of("Restock Pos: " + options.pos));
                    return 1;
                }))
                .then(literal("restockPos")
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(context -> {
                                                    options.pos = new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"));
                                                    saveConfig();
                                                    return 1;
                                                }))))
                        .then(literal("desel").executes(context -> {
                            options.pos = new BlockPos(0, 0, 0);
                            saveConfig();
                            return 1;
                        })))
                .then(literal("drop").then(argument("boolean", BoolArgumentType.bool()).executes(context -> {
                    options.drop = BoolArgumentType.getBool(context, "boolean");
                    saveConfig();
                    return 1;
                })))
                .then(literal("tradeSlot")
                        .then(argument("slot", IntegerArgumentType.integer()).executes(context -> {
                            options.slot = IntegerArgumentType.getInteger(context, "slot");
                            saveConfig();
                            return 1;
                        }))));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (LUCIANO.wasPressed()) {
                on = !on;
                resetStages();
            }
            if (last != on) {
                if (!on) {
                    targetVillager = null;
                    message("Script toggled off");
                } else {
                    message("Script toggled on");
                }
            }
            last = on;
            if (on) script();
        });
    }

    public static void resetStages() {
        guiOpened = false;
        goThrowGateWayPart1OmgSoEpic = false;
        traded = false;
        goThrowGateWayPart2OmgSoEpicWhenSexPart2 = false;
        targetVillager = null;
        LUCIANO.setPressed(true);
        LUCIANO.setPressed(false);
        once = false;
    }

    public static Vec3d getEyesPos() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        return new Vec3d(player.getX(),
                player.getY() + player.getEyeHeight(player.getPose()),
                player.getZ());
    }

    public static void script() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!guiOpened) {
            if (null == targetVillager) {
                targetVillager = filterEntities(StreamSupport.stream(client.world.getEntities().spliterator(), true));
            }
            if (targetVillager == null) {
                scriptError("No target found");
                return;
            }
            client.interactionManager.interactEntity(player, targetVillager, Hand.MAIN_HAND);
            guiOpened = true;
            pos = player.getBlockPos();
        } else if (!goThrowGateWayPart1OmgSoEpic) {
            if (getBlocksMoved(pos, player.getBlockPos()) > 10) {
                goThrowGateWayPart1OmgSoEpic = true;
            }
        } else if (!traded) {
            if (timer >= 20) {
                villagerTradeEverythingPossibleWithAllFavoritedTrades();
                pos1 = player.getBlockPos();
                traded = true;
                timer = 0;
                client.setScreen(null);
            } else {
                timer++;
            }
        } else if (!goThrowGateWayPart2OmgSoEpicWhenSexPart2) {
            if (getBlocksMoved(pos1, player.getBlockPos()) > 10) {
                goThrowGateWayPart2OmgSoEpicWhenSexPart2 = true;
            }
        } else {
            resetStages();
        }
    }

    public static void tryRestock() {
        try {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;

            BlockHitResult hitResult = new BlockHitResult(new Vec3d(0.5, 0.5, 0.5), Direction.UP, options.pos,
                    false);
            MinecraftClient.getInstance().interactionManager.interactBlock(player, MinecraftClient.getInstance().world, Hand.MAIN_HAND, hitResult);
            if (options.drop) {
                BlockState blockState = MinecraftClient.getInstance().world.getBlockState(options.pos);
                if (blockState.getBlock() instanceof ShulkerBoxBlock) {
                    ShulkerBoxBlockEntity block = (ShulkerBoxBlockEntity) MinecraftClient.getInstance().world.getBlockEntity(options.pos);
                    block.onOpen(player);
                    int k;
                    int l;
                    for (k = 0; k < 3; ++k) {
                        for (l = 0; l < 9; ++l) {

                            shiftClickSlot(((ShulkerBoxScreen) MinecraftClient.getInstance().currentScreen).getScreenHandler(), l + k * 9);

                        }
                    }
                }
            }
        } catch (Exception e){}
    }

    public static boolean villagerTradeEverythingPossibleWithAllFavoritedTrades() {
        Screen screen = MinecraftClient.getInstance().currentScreen;

        if (screen instanceof MerchantScreen) {
            if (((MerchantScreen) screen).getScreenHandler().getRecipes().size() > options.slot) {
                MerchantScreenHandler handler = ((MerchantScreen) screen).getScreenHandler();
                switchToTradeByVisibleIndex(options.slot);
                villagerTradeEverythingPossibleWithTrade(options.slot);
                villagerClearTradeInputSlots();
            }

            return true;
        }

        return false;
    }

    public static void villagerTradeEverythingPossibleWithTrade(int visibleIndex) {
        if (MinecraftClient.getInstance().currentScreen instanceof MerchantScreen) {
            MerchantScreen merchantGui = (MerchantScreen) MinecraftClient.getInstance().currentScreen;
            MerchantScreenHandler handler = merchantGui.getScreenHandler();
            TradeOutputSlot slot1 = (TradeOutputSlot) handler.getSlot(2);
            ItemStack sellItem = handler.getRecipes().get(visibleIndex).getSellItem().copy();
            while (true) {
                switchToTradeByVisibleIndex(visibleIndex);
                if (areStacksEqual(sellItem, slot1.getStack()) == false) {
                    break;
                }
                if (options.drop) {
                    int times = slot1.inventory.getStack(0).getCount() / handler.getRecipes().get(options.slot).getAdjustedFirstBuyItem().getCount();
                    for (int i = 0; i < times; i++) {
                        MinecraftClient.getInstance().interactionManager.clickSlot(handler.syncId, slot1.id, 0, SlotActionType.THROW, MinecraftClient.getInstance().player);
                    }
                } else {
                    shiftClickSlot(merchantGui, slot1.id);
                }
                if (slot1.hasStack()) {
                    break;
                }
            }
            villagerClearTradeInputSlots();
        }
    }

    public static boolean areStacksEqual(ItemStack stack1, ItemStack stack2) {
        return stack1.isEmpty() == false && stack1.isItemEqual(stack2) && ItemStack.areNbtEqual(stack1, stack2);
    }

    public static int getSelectedMerchantRecipe(MerchantScreen gui) {
        return ((IMixinMerchantScreen) gui).getSelectedMerchantRecipe();
    }

    private static void tryMoveItemsToMerchantBuySlots(MerchantScreen gui, boolean fillStacks) {
        TradeOfferList list = gui.getScreenHandler().getRecipes();
        int index = getSelectedMerchantRecipe(gui);

        if (list == null || list.size() <= index) {
            return;
        }

        TradeOffer recipe = list.get(index);

        if (recipe == null) {
            return;
        }

        ItemStack buy1 = recipe.getAdjustedFirstBuyItem();
        ItemStack buy2 = recipe.getSecondBuyItem();

        if (isStackEmpty(buy1) == false) {
            fillBuySlot(gui, 0, buy1, fillStacks);
        }

        if (isStackEmpty(buy2) == false) {
            fillBuySlot(gui, 1, buy2, fillStacks);
        }
    }

    public static int getStackSize(ItemStack stack) {
        return stack.getCount();
    }

    public static void leftClickSlot(HandledScreen<? extends ScreenHandler> gui, int slotNum) {
        clickSlot(gui, slotNum, 0, SlotActionType.PICKUP);
    }

    private static boolean clickSlotsToMoveItems(HandledScreen<? extends ScreenHandler> gui, int slotFrom, int slotTo) {
        leftClickSlot(gui, slotFrom);

        if (isStackEmpty(gui.getScreenHandler().getCursorStack())) {
            return false;
        }

        boolean ret = true;
        int size = getStackSize(gui.getScreenHandler().getCursorStack());

        leftClickSlot(gui, slotTo);

        if (isStackEmpty(gui.getScreenHandler().getCursorStack()) == false) {
            ret = getStackSize(gui.getScreenHandler().getCursorStack()) != size;

            leftClickSlot(gui, slotFrom);
        }

        return ret;
    }

    private static void moveItemsFromInventory(HandledScreen<? extends ScreenHandler> gui, int slotTo, Inventory invSrc, ItemStack stackTemplate, boolean fillStacks) {
        ScreenHandler container = gui.getScreenHandler();

        for (Slot slot : container.slots) {
            if (slot == null) {
                continue;
            }

            if (slot.inventory == invSrc && areStacksEqual(stackTemplate, slot.getStack())) {
                if (fillStacks) {
                    if (clickSlotsToMoveItems(gui, slot.id, slotTo) == false) {
                        break;
                    }
                } else {
                    clickSlotsToMoveSingleItem(gui, slot.id, slotTo);
                    break;
                }
            }
        }
    }

    private static boolean clickSlotsToMoveSingleItem(HandledScreen<? extends ScreenHandler> gui, int slotFrom, int slotTo) {
        ItemStack stack = gui.getScreenHandler().slots.get(slotFrom).getStack();

        if (isStackEmpty(stack)) {
            return false;
        }

        if (getStackSize(stack) > 1) {
            rightClickSlot(gui, slotFrom);
        } else {
            leftClickSlot(gui, slotFrom);
        }

        rightClickSlot(gui, slotTo);

        if (isStackEmpty(gui.getScreenHandler().getCursorStack()) == false) {
            leftClickSlot(gui, slotFrom);
        }

        return true;
    }

    public static void rightClickSlot(HandledScreen<? extends ScreenHandler> gui, int slotNum) {
        clickSlot(gui, slotNum, 1, SlotActionType.PICKUP);
    }

    private static void fillBuySlot(HandledScreen<? extends ScreenHandler> gui, int slotNum, ItemStack buyStack, boolean fillStacks) {
        Slot slot = gui.getScreenHandler().getSlot(slotNum);
        ItemStack existingStack = slot.getStack();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (isStackEmpty(existingStack) == false && areStacksEqual(buyStack, existingStack) == false) {
            shiftClickSlot(gui, slotNum);
        }

        existingStack = slot.getStack();

        if (isStackEmpty(existingStack) || areStacksEqual(buyStack, existingStack)) {
            moveItemsFromInventory(gui, slotNum, mc.player.getInventory(), buyStack, fillStacks);
        }
    }

    public static boolean isStackEmpty(ItemStack stack) {
        return stack.isEmpty();
    }

    public static boolean switchToTradeByVisibleIndex(int visibleIndex) {
        Screen screen = MinecraftClient.getInstance().currentScreen;

        if (screen instanceof MerchantScreen) {
            MerchantScreen merchantScreen = (MerchantScreen) screen;
            MerchantScreenHandler handler = merchantScreen.getScreenHandler();

            int realIndex = getRealTradeIndexFor(visibleIndex, handler);

            if (realIndex >= 0) {
                handler.setRecipeIndex(realIndex);

                handler.switchTo(visibleIndex);

                MinecraftClient.getInstance().getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(realIndex));

                return true;
            }
        }

        return false;
    }

    public static int getRealTradeIndexFor(int visibleIndex, MerchantScreenHandler handler) {

        TradeOfferList originalList = handler.getRecipes();
        TradeOfferList customList = handler.getRecipes();

        if (originalList != null && customList != null &&
                visibleIndex >= 0 && visibleIndex < customList.size()) {
            TradeOffer trade = customList.get(visibleIndex);

            if (trade != null) {
                int realIndex = originalList.indexOf(trade);

                if (realIndex >= 0 && realIndex < originalList.size()) {
                    return realIndex;
                }
            }
        }
        return -1;
    }

    public static void villagerClearTradeInputSlots() {
        if (MinecraftClient.getInstance().currentScreen instanceof MerchantScreen) {
            MerchantScreen merchantGui = (MerchantScreen) MinecraftClient.getInstance().currentScreen;
            Slot slot = merchantGui.getScreenHandler().getSlot(0);

            if (slot.hasStack()) {
                shiftClickSlot(merchantGui, slot.id);
            }

            slot = merchantGui.getScreenHandler().getSlot(1);

            if (slot.hasStack()) {
                shiftClickSlot(merchantGui, slot.id);
            }
        }
    }

    public static void shiftClickSlot(ScreenHandler gui, int slotNum) {
        clickSlot(gui, slotNum, 0, SlotActionType.QUICK_MOVE);
    }

    public static void shiftClickSlot(HandledScreen<? extends ScreenHandler> gui, int slotNum) {
        clickSlot(gui, slotNum, 0, SlotActionType.QUICK_MOVE);
    }

    public static void clickSlot(HandledScreen<? extends ScreenHandler> gui, int slotNum, int mouseButton, SlotActionType type) {
        if (slotNum >= 0 && slotNum < gui.getScreenHandler().slots.size()) {
            Slot slot = gui.getScreenHandler().getSlot(slotNum);
            clickSlot(gui, slot, slotNum, mouseButton, type);
        } else {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.interactionManager.clickSlot(gui.getScreenHandler().syncId, slotNum, mouseButton, type, mc.player);
            } catch (Exception e) {

            }
        }
    }

    public static void clickSlot(ScreenHandler gui, int slotNum, int mouseButton, SlotActionType type) {
        if (slotNum >= 0 && slotNum < gui.slots.size()) {
            Slot slot = gui.getSlot(slotNum);
            clickSlot(gui, slot, slotNum, mouseButton, type);
        } else {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.interactionManager.clickSlot(gui.syncId, slotNum, mouseButton, type, mc.player);
            } catch (Exception e) {
            }
        }
    }

    public static void clickSlot(ScreenHandler gui, Slot slot, int slotNum, int mouseButton, SlotActionType type) {
        try {
            handleMouseClick(gui, slot, slotNum, mouseButton, type);
        } catch (Exception e) {

        }
    }

    public static void handleMouseClick(ScreenHandler gui, Slot slotIn, int slotId, int mouseButton, SlotActionType type) {
        ((IMixinScreenWithHandler) gui).handleMouseClickInvoker(slotIn, slotId, mouseButton, type);
    }

    public static void clickSlot(HandledScreen<? extends ScreenHandler> gui, Slot slot, int slotNum, int mouseButton, SlotActionType type) {
        try {
            handleMouseClick(gui, slot, slotNum, mouseButton, type);
        } catch (Exception e) {

        }
    }

    public static void handleMouseClick(HandledScreen<?> gui, Slot slotIn, int slotId, int mouseButton, SlotActionType type) {
        ((IMixinScreenWithHandler) gui).handleMouseClickInvoker(slotIn, slotId, mouseButton, type);
    }

    private static boolean isHotbarSlot(int slot) {
        return slot >= 36 && slot < (36 + PlayerInventory.getHotbarSize());
    }

    public static int getBlocksMoved(BlockPos pos1, BlockPos pos2) {
        int x = Math.abs(pos1.getX()) > Math.abs(pos2.getX()) ? Math.abs(pos1.getX()) - Math.abs(pos2.getX()) : Math.abs(pos2.getX()) - Math.abs(pos1.getX());
        int z = Math.abs(pos1.getZ()) > Math.abs(pos2.getZ()) ? Math.abs(pos1.getZ()) - Math.abs(pos2.getZ()) : Math.abs(pos2.getZ()) - Math.abs(pos1.getZ());
        return x + z;
    }

    private static Entity filterEntities(Stream<Entity> s) {
        Stream<Entity> stream = s.filter(Objects::nonNull).filter(e -> e instanceof VillagerEntity && ((LivingEntity) e).getHealth() > 0);
        for (Iterator<Entity> it = stream.iterator(); it.hasNext(); ) {
            Entity e = it.next();
            if (null != e) {
                return e;
            }
        }
        scriptError("No villagers near");
        return null;
    }

    public static void scriptError(String message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText("[TradeScript]§4 " + message + "turning macro off").setStyle(Style.EMPTY.withFont(FONT)));
    }

    public static void message(String message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText("[TradeScript]" + message).setStyle(Style.EMPTY.withFont(FONT)));
    }
}
