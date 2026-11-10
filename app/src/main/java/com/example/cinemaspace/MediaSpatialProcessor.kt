@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.cinemaspace

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpatialAudioProcessor : BaseAudioProcessor() {
    private var engine: SpatialEngine? = null
    private var inCh = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in listOf(1, 2, 6, 8)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inCh = inputAudioFormat.channelCount
        engine = SpatialEngine(inputAudioFormat.sampleRate, inCh)
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * inCh)
        if (frames == 0) return
        val inp = ShortArray(frames * inCh)
        inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inp)
        inputBuffer.position(inputBuffer.position() + frames * inCh * 2)
        val out = ShortArray(frames * 2)
        engine!!.process(inp, frames, out, SpatialHub.state.value)
        val ob = replaceOutputBuffer(frames * 4)
        ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(out)
        ob.position(frames * 4)
        ob.flip()
    }
}

fun spatialRenderers(ctx: Context) = object : DefaultRenderersFactory(ctx) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf<AudioProcessor>(SpatialAudioProcessor()))
        .build()
}
