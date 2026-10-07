plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
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
    }
}
