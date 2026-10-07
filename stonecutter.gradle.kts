plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
            replace("import net.minecraft.Util;", "import net.minecraft.util.Util;")
            replace("import net.minecraft.world.level.GameRules;", "import net.minecraft.world.level.gamerules.GameRules;")
        }
        // 1.21.9 reworked input: key presses and clicks are records, list rows know their own area, and the
        // search hint has a style constant. Before, the mod brings small stand-ins (package legacy).
        string(current.parsed >= "1.21.9") {
            replace("import io.github.autyism.keybindprofilesplus.legacy.KeyEvent;", "import net.minecraft.client.input.KeyEvent;")
            replace("import io.github.autyism.keybindprofilesplus.legacy.MouseButtonEvent;", "import net.minecraft.client.input.MouseButtonEvent;")
            replace("import io.github.autyism.keybindprofilesplus.legacy.MouseButtonInfo;", "import net.minecraft.client.input.MouseButtonInfo;")
            replace("import io.github.autyism.keybindprofilesplus.legacy.CharacterEvent;", "import net.minecraft.client.input.CharacterEvent;")
            replace("extends io.github.autyism.keybindprofilesplus.legacy.ListEntry<", "extends ContainerObjectSelectionList.Entry<")
            replace("extends io.github.autyism.keybindprofilesplus.legacy.SelectionList<", "extends ContainerObjectSelectionList<")
            replace("ensureVisible(", "scrollToEntry(")
            replace("net.minecraft.network.chat.Style.EMPTY.applyFormats(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC)", "EditBox.SEARCH_HINT_STYLE")
            replace("InputConstants.getKey(input.key(), input.scancode())", "InputConstants.getKey(input)")
            replace("KeyboardHandler keyPress (JIIII)V", "KeyboardHandler keyPress (JILnet/minecraft/client/input/KeyEvent;)V")
            // key binding categories were plain translation keys
            replace("KeyMapping.CATEGORY_MISC", "KeyMapping.Category.MISC")
            replace("KeyMapping.CATEGORY_MOVEMENT", "KeyMapping.Category.MOVEMENT")
            replace("\"keys/key.categories.movement\"", "\"keys/minecraft:movement\"")
            replace("\"keys/key.categories.multiplayer\"", "\"keys/minecraft:multiplayer\"")
        }
        // 1.21.11 takes a cycle button's first value in builder(); before, it was set with withInitialValue()
        regex(current.parsed >= "1.21.11") {
            replace(
                "(CycleButton\\.<\\w+>builder\\()([\\w:]+)\\)\\.withInitialValue\\((\\w+)\\)", "$1$2, $3)",
                "(CycleButton\\.<\\w+>builder\\()([\\w:]+), (\\w+)\\)", "$1$2).withInitialValue($3)"
            )
        }

        // 26.1+ is not obfuscated: the access widener is read in the game's own names
        string(current.parsed >= "26.1") {
            replace("accessWidener v2 named", "classTweaker v2 official")
        }

        // 26.1 renamed GUI drawing: GuiGraphics -> GuiGraphicsExtractor, render... -> extract...
        regex(current.parsed >= "26.1") {
            replace("\\bGuiGraphics\\b", "GuiGraphicsExtractor", "\\bGuiGraphicsExtractor\\b", "GuiGraphics")
        }
        regex(current.parsed >= "26.1") {
            replace("\\brenderContent\\b", "extractContent", "\\bextractContent\\b", "renderContent")
        }
        regex(current.parsed >= "26.1") {
            replace(
                "\\bvoid render\\(GuiGraphics context, int mouseX, int mouseY, float deltaTicks\\)", "void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks)",
                "\\bvoid extractRenderState\\(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks\\)", "void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks)"
            )
        }
        regex(current.parsed >= "26.1") {
            replace("\\.render\\(context, mouseX, mouseY, deltaTicks\\)", ".extractRenderState(context, mouseX, mouseY, deltaTicks)", "\\.extractRenderState\\(context, mouseX, mouseY, deltaTicks\\)", ".render(context, mouseX, mouseY, deltaTicks)")
        }
        regex(current.parsed >= "26.1") {
            replace("\\bcontext\\.drawString\\(", "context.text(", "\\bcontext\\.text\\(", "context.drawString(")
        }
        regex(current.parsed >= "26.1") {
            replace("\\bcontext\\.drawCenteredString\\(", "context.centeredText(", "\\bcontext\\.centeredText\\(", "context.drawCenteredString(")
        }
        // Fabric API 26.1: key bindings are key mappings, screen render events are extract events
        string(current.parsed >= "26.1") {
            replace("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper")
            replace("KeyBindingHelper.registerKeyBinding(", "KeyMappingHelper.registerKeyMapping(")
            replace("ScreenEvents.afterRender(", "ScreenEvents.afterExtract(")
            replace("ScreenEvents.beforeRender(", "ScreenEvents.beforeExtract(")
            replace("Screens.getButtons(", "Screens.getWidgets(")
        }

        // 26.2 moved the open screen from Minecraft to Gui (only these receivers are a Minecraft in this code)
        regex(current.parsed >= "26.2") {
            replace(
                "((?<![.\\w])minecraft|(?<![.\\w])client|\\bclient\\(\\)|\\bMinecraft\\.getInstance\\(\\))\\.setScreen\\(", "$1.gui.setScreen(",
                "((?<![.\\w])minecraft|(?<![.\\w])client|\\bclient\\(\\)|\\bMinecraft\\.getInstance\\(\\))\\.gui\\.setScreen\\(", "$1.setScreen("
            )
        }
        regex(current.parsed >= "26.2") {
            replace(
                "((?<![.\\w])minecraft|(?<![.\\w])client|\\bclient\\(\\))\\.screen\\b", "$1.gui.screen()",
                "((?<![.\\w])minecraft|(?<![.\\w])client|\\bclient\\(\\))\\.gui\\.screen\\(\\)", "$1.screen"
            )
        }
        regex(current.parsed >= "26.2") {
            replace("\\bclient\\.getOverlay\\(\\)", "client.gui.overlay()", "\\bclient\\.gui\\.overlay\\(\\)", "client.getOverlay()")
        }

        // 26.3 reads the keyboard through SDL: keyboard keys are a key type of their own, and the modifier
        // bits of input events are SDL's (this mod keeps its own Ctrl / Shift / Alt bits)
        string(current.parsed >= "26.3") {
            replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
        }
        regex(current.parsed >= "26.3") {
            replace(
                "\\b(input|click)\\.modifiers\\(\\)", "io.github.autyism.keybindprofilesplus.input.SdlKeys.toKbpModifiers($1.modifiers())",
                "io\\.github\\.autyism\\.keybindprofilesplus\\.input\\.SdlKeys\\.toKbpModifiers\\((input|click)\\.modifiers\\(\\)\\)", "$1.modifiers()"
            )
        }
    }
}
