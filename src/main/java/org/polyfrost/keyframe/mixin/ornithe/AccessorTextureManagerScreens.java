package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TextureManager.class)
public interface AccessorTextureManagerScreens {

    @Accessor("textures")
    Map<Identifier, Texture> keyframe$textures();

}
