package org.polyfrost.keyframe.standalone.export

//? if >=1.21.8 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.textures.GpuTexture
//?}
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.polyfrost.keyframe.standalone.compat.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

internal class Nv12Converter private constructor(
	private val program: Int,
	private val vertexArray: Int,
	private val framebuffer: Int,
	private val target: Int,
	private val width: Int,
	private val height: Int,
) : AutoCloseable {
	fun convert(texture: GpuTexture, buffer: GpuBuffer) {
		val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
		val vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
		val drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
		val readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
		val packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)
		val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
		val viewport = ints(GL11.GL_VIEWPORT)
		val colorMask = ints(GL11.GL_COLOR_WRITEMASK)
		val packRowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH)
		val packSkipRows = GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS)
		val packSkipPixels = GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS)
		val enabled = TOGGLES.filter(GL11::glIsEnabled)

		GL13.glActiveTexture(GL13.GL_TEXTURE0)
		val boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId(texture)!!)
		TOGGLES.forEach(GL11::glDisable)
		GL11.glColorMask(true, true, true, true)
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
		GL11.glViewport(0, 0, width / 4, height * 3 / 2)
		GL20.glUseProgram(this.program)
		GL30.glBindVertexArray(this.vertexArray)
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3)

		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, glBufferId(buffer)!!)
		GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0)
		GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0)
		GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0)
		GL11.glReadPixels(0, 0, width / 4, height * 3 / 2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L)

		GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, packRowLength)
		GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, packSkipRows)
		GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, packSkipPixels)
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer)
		GL30.glBindVertexArray(vertexArray)
		GL20.glUseProgram(program)
		GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3))
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
		GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0)
		enabled.forEach(GL11::glEnable)
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, boundTexture)
		GL13.glActiveTexture(activeTexture)
	}

	private var closed = false

	override fun close() {
		if (closed) return
		closed = true
		GL20.glDeleteProgram(program)
		GL30.glDeleteVertexArrays(vertexArray)
		GL30.glDeleteFramebuffers(framebuffer)
		GL11.glDeleteTextures(target)
	}

	companion object {
		private val LOGGER = LoggerFactory.getLogger("Keyframe")
		private val TOGGLES = intArrayOf(GL11.GL_BLEND, GL11.GL_SCISSOR_TEST, GL11.GL_CULL_FACE, GL11.GL_DEPTH_TEST, GL11.GL_STENCIL_TEST).toList()

		private const val INFO_LOG_LENGTH = 4096

		private fun ints(name: Int) = BufferUtils.createIntBuffer(16).also { GL11.glGetIntegerv(name, it) }

		private fun source(name: String) = Nv12Converter::class.java.getResource("/assets/keyframe/shaders/$name")!!.readText()

		private fun compile(type: Int, name: String): Int? {
			val shader = GL20.glCreateShader(type)
			GL20.glShaderSource(shader, source(name))
			GL20.glCompileShader(shader)
			if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE) return shader
			LOGGER.warn("Could not compile {}, exporting RGB: {}", name, GL20.glGetShaderInfoLog(shader, INFO_LOG_LENGTH))
			GL20.glDeleteShader(shader)
			return null
		}

		private fun link(): Int? {
			val vertex = compile(GL20.GL_VERTEX_SHADER, "nv12.vsh") ?: return null
			val fragment = compile(GL20.GL_FRAGMENT_SHADER, "nv12.fsh") ?: return null.also { GL20.glDeleteShader(vertex) }
			val program = GL20.glCreateProgram()
			GL20.glAttachShader(program, vertex)
			GL20.glAttachShader(program, fragment)
			GL20.glLinkProgram(program)
			GL20.glDeleteShader(vertex)
			GL20.glDeleteShader(fragment)
			if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_FALSE) return program
			LOGGER.warn("Could not link the NV12 shader, exporting RGB: {}", GL20.glGetProgramInfoLog(program, INFO_LOG_LENGTH))
			GL20.glDeleteProgram(program)
			return null
		}

		fun create(width: Int, height: Int, texture: GpuTexture): Nv12Converter? {
			if (width % 4 != 0 || height % 2 != 0) return null
			if (!supportsGl32()) return null
			glTextureId(texture) ?: return null
			val program = link() ?: return null

			val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
			val target = GL11.glGenTextures()
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, target)
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width / 4, height * 3 / 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null as ByteBuffer?)
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)

			val previousFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
			val framebuffer = GL30.glGenFramebuffers()
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer)
			GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, target, 0)
			val complete = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer)

			val converter = Nv12Converter(program, GL30.glGenVertexArrays(), framebuffer, target, width, height)
			if (complete) return converter
			LOGGER.warn("Could not create the NV12 framebuffer, exporting RGB")
			converter.close()
			return null
		}
	}
}
