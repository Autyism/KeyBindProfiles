package io.github.autyism.keybindprofilesplus.selftest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.resources.Identifier;
*///?}

/**
 * The machinery of the self-test: a queue of steps run one per tick (with waits in between so
 * screens get drawn), pass / fail bookkeeping, screenshots, and helpers that operate the game the
 * way a player would (clicking buttons by their label, typing into fields, mouse clicks, key
 * events through the game's real key handler).
 */
abstract class SelfTestRunner {
    static final String LOG_PREFIX = "[SelfTest] ";
    static final int SCREEN_SETTLE_TICKS = 8;
    private static final int SHOT_TICKS = 4;
    private static final int MAX_RUN_TICKS = 20 * 280;

    protected final ProfileService service;
    protected Screen homeScreen;

    private final Deque<Step> steps = new ArrayDeque<>();
    private final AtomicInteger pendingScreenshots = new AtomicInteger();
    private boolean busy;
    private BooleanSupplier waitCondition;
    private String waitName;
    private int waitDeadline;
    private int waitTicks;
    private int runTicks;
    private int passed;
    private int failed;
    private int screenshotIndex;
    //? if >=26.1 {
    /*// Frames drawn so far. 26.1+ can run two catch-up ticks before it draws the next frame, so a screenshot taken
    // right after a step could still show the screen from before it: screenshots wait for a newly drawn frame.
    private static int framesDrawn;
    *///?}

    SelfTestRunner(ProfileService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ driver

    /** Runs the next step if it is time. Returns true once every step has run and all screenshots are saved. */
    final boolean advance() {
        if (busy) {
            return false;
        }
        if (++runTicks > MAX_RUN_TICKS) {
            fail("self-test exceeded " + MAX_RUN_TICKS + " ticks, aborting");
            steps.clear();
            waitCondition = null;
            return true;
        }

        if (waitCondition != null) {
            if (waitCondition.getAsBoolean()) {
                waitCondition = null;
            } else if (runTicks > waitDeadline) {
                fail("timed out waiting for: " + waitName);
                waitCondition = null;
            } else {
                return false;
            }
        }
        if (waitTicks > 0) {
            waitTicks--;
            return false;
        }

        Step step = steps.poll();
        if (step == null) {
            return pendingScreenshots.get() == 0;
        }

        busy = true;
        try {
            log("STEP " + step.name());
            step.action().run();
        } catch (Throwable t) {
            fail(step.name() + " threw " + t);
            KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "exception in step '" + step.name() + "'", t);
        } finally {
            busy = false;
        }
        waitTicks = step.waitAfter();
        if (step.until() != null) {
            waitCondition = step.until();
            waitName = step.name();
            waitDeadline = runTicks + step.timeoutTicks();
        }
        return false;
    }

    final int stepCount() {
        return steps.size();
    }

    final String summary() {
        return "passed=" + passed + " failed=" + failed + " screenshots=" + screenshotIndex;
    }

    // ------------------------------------------------------------------ building the scenario

    final void step(String name, int waitAfter, Runnable action) {
        steps.add(new Step(name, waitAfter, action, null, 0));
    }

    final void step(String name, Runnable action) {
        step(name, 0, action);
    }

    final void stepUntil(String name, Runnable action, BooleanSupplier until, int timeoutTicks) {
        steps.add(new Step(name, 0, action, until, timeoutTicks));
    }

    final void open(String name, Supplier<Screen> screen) {
        step("open " + name, SCREEN_SETTLE_TICKS, () -> client().setScreen(screen.get()));
    }

    /** Clicks a button by its label and waits for the screen that opens to be drawn. */
    final void clickStep(String translationKey) {
        step("click " + translationKey, SCREEN_SETTLE_TICKS, () -> click(translated(translationKey)));
    }

    final void shot(String name) {
        //? if >=26.1 {
        /*int[] seen = new int[1];
        stepUntil("frame for screenshot " + name, () -> seen[0] = framesDrawn, () -> framesDrawn > seen[0], 40);
        *///?}
        step("screenshot " + name, SHOT_TICKS, () -> screenshot(name));
    }

    //? if >=26.1 {
    /*static void countFrames() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterExtract(screen).register((current, context, mouseX, mouseY, tickDelta) -> framesDrawn++));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(KeyBindProfilesPlus.MOD_ID, "selftest_frames"), (context, tickCounter) -> framesDrawn++);
    }
    *///?}

    // ------------------------------------------------------------------ results

    final void check(String what, boolean ok) {
        if (ok) {
            pass(what);
        } else {
            fail(what);
        }
    }

    final void pass(String what) {
        passed++;
        log("PASS " + what);
    }

    final void fail(String what) {
        failed++;
        KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "FAIL " + what);
    }

    static void log(String message) {
        KeyBindProfilesPlus.LOGGER.info(LOG_PREFIX + message);
    }

    // ------------------------------------------------------------------ operating the game

    static Minecraft client() {
        return Minecraft.getInstance();
    }

    static String translated(String translationKey, Object... args) {
        return Component.translatable(translationKey, args).getString();
    }

    @SuppressWarnings("unchecked")
    final <T extends Screen> T screen(Class<T> type) {
        Screen current = client().screen;
        if (!type.isInstance(current)) {
            throw new IllegalStateException("expected " + type.getSimpleName() + " but the screen is "
                    + (current == null ? "none" : current.getClass().getSimpleName()));
        }
        return (T) current;
    }

    final boolean isScreen(Class<? extends Screen> type) {
        return type.isInstance(client().screen);
    }

    /** Presses the button (anywhere on the current screen, lists included) that shows this label. */
    final boolean click(String label) {
        AbstractWidget widget = findWidget(client().screen, label);
        String where = client().screen == null ? "no screen" : client().screen.getClass().getSimpleName();
        if (!(widget instanceof AbstractButton pressable)) {
            fail("no button labelled '" + label + "' on " + where);
            return false;
        }
        if (!pressable.active) {
            fail("button '" + label + "' on " + where + " is disabled");
            return false;
        }
        //? if >=1.21.9 {
        pressable.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        //?} else
        /*pressable.onPress();*/
        pass("clicked '" + label + "'");
        return true;
    }

    final boolean hasWidget(String label) {
        return findWidget(client().screen, label) != null;
    }

    final AbstractWidget widget(String label) {
        return findWidget(client().screen, label);
    }

    /** Types into the text field with this name (its narration label), replacing what was there. */
    final boolean type(String fieldTranslationKey, String text) {
        if (findWidget(client().screen, translated(fieldTranslationKey)) instanceof EditBox field) {
            field.setValue(text);
            return true;
        }
        fail("no text field '" + translated(fieldTranslationKey) + "' on the current screen");
        return false;
    }

    static AbstractWidget findWidget(ContainerEventHandler parent, String label) {
        if (parent == null) {
            return null;
        }
        for (GuiEventListener element : parent.children()) {
            if (element instanceof AbstractWidget widget && label.equals(widget.getMessage().getString())) {
                return widget;
            }
            if (element instanceof ContainerEventHandler nested) {
                AbstractWidget found = findWidget(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A left click at screen coordinates, through the screen like a real mouse click. */
    final void mouseClick(int[] point, int modifiers) {
        if (point == null || client().screen == null) {
            fail("nothing to click at");
            return;
        }
        //? if >=26.3 {
        /*client().screen.mouseClicked(new MouseButtonEvent(point[0], point[1],
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, io.github.autyism.keybindprofilesplus.input.SdlKeys.toSdlModifiers(modifiers))), false);
        *///?} else if >=1.21.9 {
        client().screen.mouseClicked(new MouseButtonEvent(point[0], point[1], new MouseButtonInfo(0, modifiers)), false);
        //?} else {
        /*// Before 1.21.9 a click carried no modifier keys and the mod reads the held ones: pretend these are held
        KeyCombos.setHeldModifiersForTesting(() -> modifiers);
        try {
            client().screen.mouseClicked(point[0], point[1], InputConstants.MOUSE_BUTTON_LEFT);
        } finally {
            KeyCombos.setHeldModifiersForTesting(null);
        }
        *///?}
    }

    /** Feeds one key event into the game exactly where GLFW would. */
    final void sendKey(int keyCode, boolean press, int modifiers) {
        //? if >=26.3 {
        /*client().keyboardHandler.keyPress(client().getWindow().handle(), press ? 1 : 0,
                new KeyEvent(keyCode, io.github.autyism.keybindprofilesplus.input.SdlKeys.sdlKeyCode(keyCode), io.github.autyism.keybindprofilesplus.input.SdlKeys.toSdlModifiers(modifiers)));
        *///?} else if >=1.21.9 {
        client().keyboardHandler.keyPress(client().getWindow().handle(), press ? 1 : 0, new KeyEvent(keyCode, 0, modifiers));
        //?} else {
        /*client().keyboardHandler.keyPress(client().getWindow().getWindow(), keyCode, 0, press ? 1 : 0, modifiers);
        *///?}
    }

    /** Binds a key or a combination given in text form ("ctrl+key.keyboard.x"). */
    final void bind(String bindingId, String value) {
        KeyMapping binding = KeyMapping.get(bindingId);
        if (binding != null) {
            KeyCombos.applyValue(binding, value);
            KeyMapping.resetMapping();
        }
    }

    static KeyMapping binding(String bindingId) {
        KeyMapping binding = KeyMapping.get(bindingId);
        if (binding == null) {
            throw new IllegalStateException("missing key binding " + bindingId);
        }
        return binding;
    }

    static String keyLabel(String bindingId) {
        return binding(bindingId).getTranslatedKeyMessage().getString();
    }

    static Map<String, String> currentKeyValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (KeyMapping binding : client().options.keyMappings) {
            values.put(binding.getName(), KeyCombos.valueOf(binding));
        }
        return values;
    }

    static String liveOption(String key) {
        return GameOptionsBridge.readAll(client().options).get(key).rawValue();
    }

    static boolean drainPressed(KeyMapping binding) {
        boolean any = false;
        while (binding.consumeClick()) {
            any = true;
        }
        return any;
    }

    private void screenshot(String name) {
        Minecraft client = client();
        String fileName = String.format("selftest_%02d_%s.png", ++screenshotIndex, name);
        //? if >=26.2 {
        /*RenderTarget framebuffer = client.gameRenderer.mainRenderTarget();
        *///?} else
        RenderTarget framebuffer = client.getMainRenderTarget();
        // 4K frames are halved so the files stay small; GUI pixels are still at least 2 px wide.
        int downscale = framebuffer.width >= 3000 && framebuffer.width % 2 == 0 && framebuffer.height % 2 == 0 ? 2 : 1;
        String screenName = client.screen == null ? "none" : client.screen.getClass().getSimpleName();
        pendingScreenshots.incrementAndGet();
        //? if >=1.21.6 {
        Screenshot.grab(client.gameDirectory, fileName, framebuffer, downscale, message -> {
        //?} else
        /*Screenshot.grab(client.gameDirectory, fileName, framebuffer, message -> {*/
            pendingScreenshots.decrementAndGet();
            log("SCREENSHOT " + fileName + " screen=" + screenName);
        });
    }

    private record Step(String name, int waitAfter, Runnable action, BooleanSupplier until, int timeoutTicks) {
    }
}
