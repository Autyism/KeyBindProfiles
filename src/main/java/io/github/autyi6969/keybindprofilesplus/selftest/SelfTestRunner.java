package io.github.autyi6969.keybindprofilesplus.selftest;

import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombos;
import io.github.autyi6969.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
        step("screenshot " + name, SHOT_TICKS, () -> screenshot(name));
    }

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

    static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    static String translated(String translationKey, Object... args) {
        return Text.translatable(translationKey, args).getString();
    }

    @SuppressWarnings("unchecked")
    final <T extends Screen> T screen(Class<T> type) {
        Screen current = client().currentScreen;
        if (!type.isInstance(current)) {
            throw new IllegalStateException("expected " + type.getSimpleName() + " but the screen is "
                    + (current == null ? "none" : current.getClass().getSimpleName()));
        }
        return (T) current;
    }

    final boolean isScreen(Class<? extends Screen> type) {
        return type.isInstance(client().currentScreen);
    }

    /** Presses the button (anywhere on the current screen, lists included) that shows this label. */
    final boolean click(String label) {
        ClickableWidget widget = findWidget(client().currentScreen, label);
        String where = client().currentScreen == null ? "no screen" : client().currentScreen.getClass().getSimpleName();
        if (!(widget instanceof PressableWidget pressable)) {
            fail("no button labelled '" + label + "' on " + where);
            return false;
        }
        if (!pressable.active) {
            fail("button '" + label + "' on " + where + " is disabled");
            return false;
        }
        pressable.onPress(new KeyInput(InputUtil.GLFW_KEY_ENTER, 0, 0));
        pass("clicked '" + label + "'");
        return true;
    }

    final boolean hasWidget(String label) {
        return findWidget(client().currentScreen, label) != null;
    }

    final ClickableWidget widget(String label) {
        return findWidget(client().currentScreen, label);
    }

    /** Types into the text field with this name (its narration label), replacing what was there. */
    final boolean type(String fieldTranslationKey, String text) {
        if (findWidget(client().currentScreen, translated(fieldTranslationKey)) instanceof TextFieldWidget field) {
            field.setText(text);
            return true;
        }
        fail("no text field '" + translated(fieldTranslationKey) + "' on the current screen");
        return false;
    }

    static ClickableWidget findWidget(ParentElement parent, String label) {
        if (parent == null) {
            return null;
        }
        for (Element element : parent.children()) {
            if (element instanceof ClickableWidget widget && label.equals(widget.getMessage().getString())) {
                return widget;
            }
            if (element instanceof ParentElement nested) {
                ClickableWidget found = findWidget(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A left click at screen coordinates, through the screen like a real mouse click. */
    final void mouseClick(int[] point, int modifiers) {
        if (point == null || client().currentScreen == null) {
            fail("nothing to click at");
            return;
        }
        client().currentScreen.mouseClicked(new Click(point[0], point[1], new MouseInput(0, modifiers)), false);
    }

    /** Feeds one key event into the game exactly where GLFW would. */
    final void sendKey(int keyCode, boolean press, int modifiers) {
        client().keyboard.onKey(client().getWindow().getHandle(), press ? 1 : 0, new KeyInput(keyCode, 0, modifiers));
    }

    /** Binds a key or a combination given in text form ("ctrl+key.keyboard.x"). */
    final void bind(String bindingId, String value) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        if (binding != null) {
            KeyCombos.applyValue(binding, value);
            KeyBinding.updateKeysByCode();
        }
    }

    static KeyBinding binding(String bindingId) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        if (binding == null) {
            throw new IllegalStateException("missing key binding " + bindingId);
        }
        return binding;
    }

    static String keyLabel(String bindingId) {
        return binding(bindingId).getBoundKeyLocalizedText().getString();
    }

    static Map<String, String> currentKeyValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (KeyBinding binding : client().options.allKeys) {
            values.put(binding.getId(), KeyCombos.valueOf(binding));
        }
        return values;
    }

    static String liveOption(String key) {
        return GameOptionsBridge.readAll(client().options).get(key).rawValue();
    }

    static boolean drainPressed(KeyBinding binding) {
        boolean any = false;
        while (binding.wasPressed()) {
            any = true;
        }
        return any;
    }

    private void screenshot(String name) {
        MinecraftClient client = client();
        String fileName = String.format("selftest_%02d_%s.png", ++screenshotIndex, name);
        Framebuffer framebuffer = client.getFramebuffer();
        // 4K frames are halved so the files stay small; GUI pixels are still at least 2 px wide.
        int downscale = framebuffer.textureWidth >= 3000 && framebuffer.textureWidth % 2 == 0 && framebuffer.textureHeight % 2 == 0 ? 2 : 1;
        String screenName = client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName();
        pendingScreenshots.incrementAndGet();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, fileName, framebuffer, downscale, message -> {
            pendingScreenshots.decrementAndGet();
            log("SCREENSHOT " + fileName + " screen=" + screenName);
        });
    }

    private record Step(String name, int waitAfter, Runnable action, BooleanSupplier until, int timeoutTicks) {
    }
}
