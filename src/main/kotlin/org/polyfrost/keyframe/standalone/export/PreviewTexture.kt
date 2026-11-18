package org.polyfrost.keyframe.standalone.export

import androidx.compose.ui.geometry.Rect
import com.mojang.blaze3d.systems.RenderSystem
//? if >=1.21.8 {
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
//?}
import net.minecraft.client.Minecraft
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.editor.Crop

object PreviewTexture {
	private var texture: GpuTexture? = null
	private var view: GpuTextureView? = null

	internal var bounds: Rect? = null
		private set

	internal var crop = Crop(0f, 1f, 1f)
		private set

	internal fun place(bounds: Rect, crop: Crop) {
		this.bounds = bounds
		this.crop = crop
	}

	@JvmStatic
	fun capture() {
		val texture = texture ?: return
		val source = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture ?: return
		val width = texture.getWidth(0)
		val height = texture.getHeight(0)
		if (source.getWidth(0) != width || source.getHeight(0) != height) return
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, width, height)
	}

	fun close() {
		view?.close()
		texture?.close()
		view = null
		texture = null
	}

	internal fun target(): GpuTextureView? {
		val source = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture ?: return null
		val width = source.getWidth(0)
		val height = source.getHeight(0)
		if (texture?.getWidth(0) == width && texture?.getHeight(0) == height) return view
		close()
		val device = RenderSystem.getDevice()
		val usage = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING
		val created = device.createTexture({ "Keyframe editor preview" }, usage, source.format, width, height, 1, 1)
		texture = created
		return device.createTextureView(created).also { view = it }
	}
}
