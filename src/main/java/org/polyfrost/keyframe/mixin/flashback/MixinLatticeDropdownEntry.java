package org.polyfrost.keyframe.mixin.flashback;

//? if >=1.21.10 && <26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.moulberry.lattice.widget.DropdownWidget$Entry", remap = false)
public abstract class MixinLatticeDropdownEntry extends ObjectSelectionList.Entry<MixinLatticeDropdownEntry> {

    @Shadow
    public abstract void actuallyRender(GuiGraphicsExtractor graphics, int x, int y);

    @Override
    public void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
        this.actuallyRender(graphics, this.getContentX(), this.getContentY());
    }

}
*///?}
