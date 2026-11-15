package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.WorldTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

interface GameSoundListener {
  fun onJump()
  fun onShoot()
  fun onCoin()
  fun onPowerUp()
  fun onEnemyDefeat()
  fun onBlockHit()
  fun onCarEnter()
  fun onDamage()
  fun onWantedUp()
  fun onVictory()
  fun onBusted()
  fun onMissileLaunch()
  fun onExplosion()
}

object NoOpGameSoundListener : GameSoundListener {
  override fun onJump() = Unit
  override fun onShoot() = Unit
  override fun onCoin() = Unit
  override fun onPowerUp() = Unit
  override fun onEnemyDefeat() = Unit
  override fun onBlockHit() = Unit
  override fun onCarEnter() = Unit
  override fun onDamage() = Unit
  override fun onWantedUp() = Unit
  override fun onVictory() = Unit
  override fun onBusted() = Unit
  override fun onMissileLaunch() = Unit
  override fun onExplosion() = Unit
}

class RetroAudioEngine(
  private val worldTheme: WorldTheme,
) : GameSoundListener {

  private val sampleRate = 22050
  private val muted = AtomicBoolean(false)
  private val paused = AtomicBoolean(false)
  private val starMode = AtomicBoolean(false)
  private val released = AtomicBoolean(false)

  // Pre-rendered crisp 8-bit PCM SFX buffers
  private val jumpPcm = synthSweep(startHz = 260.0, endHz = 720.0, durationMs = 115, wave = Wave.SQUARE, vol = 0.28)
  private val shootPcm = synthSweep(startHz = 960.0, endHz = 210.0, durationMs = 95, wave = Wave.NOISE_SQUARE, vol = 0.24)
  private val coinPcm = synthNotes(listOf(987.77 to 55, 1318.51 to 130), Wave.SQUARE, 0.26)
  private val powerUpPcm =
    synthNotes(
      listOf(523.25 to 60, 659.25 to 60, 783.99 to 60, 1046.50 to 70, 1318.51 to 110),
      Wave.SQUARE,
      0.28,
    )
  private val defeatPcm = synthSweep(startHz = 360.0, endHz = 85.0, durationMs = 135, wave = Wave.NOISE_SQUARE, vol = 0.28)
  private val blockHitPcm = synthSweep(startHz = 210.0, endHz = 125.0, durationMs = 80, wave = Wave.TRIANGLE, vol = 0.30)
  private val carEnterPcm = synthSweep(startHz = 105.0, endHz = 265.0, durationMs = 210, wave = Wave.SAW, vol = 0.25)
  private val damagePcm = synthSweep(startHz = 185.0, endHz = 65.0, durationMs = 220, wave = Wave.SAW, vol = 0.30)
  private val wantedPcm =
    synthNotes(listOf(740.0 to 75, 987.0 to 75, 740.0 to 75, 987.0 to 85), Wave.SQUARE, 0.22)
  private val victoryPcm =
    synthNotes(
      listOf(523.25 to 95, 659.25 to 95, 783.99 to 95, 1046.50 to 160, 783.99 to 95, 1046.50 to 260),
      Wave.SQUARE,
      0.28,
    )
  private val bustedPcm =
    synthNotes(
      listOf(392.0 to 120, 369.99 to 120, 349.23 to 120, 329.63 to 140, 261.63 to 240),
      Wave.SAW,
      0.28,
    )
  private val missilePcm = synthSweep(startHz = 520.0, endHz = 140.0, durationMs = 175, wave = Wave.NOISE_SQUARE, vol = 0.28)
  private val explosionPcm = synthSweep(startHz = 160.0, endHz = 42.0, durationMs = 260, wave = Wave.NOISE_SQUARE, vol = 0.34)

  private class ActiveVoice(
    val pcm: ShortArray,
    var pos: Int = 0,
  )

  private val activeVoices = mutableListOf<ActiveVoice>()
  private val voiceLock = Any()
  private val bgmThread: Thread
  private val sfxThread: Thread

  init {
    bgmThread =
      Thread(
          { runBackgroundMusicLoop() },
          "ShroomAutoBgm",
        )
        .apply {
          isDaemon = true
          start()
        }
    sfxThread =
      Thread(
          { runSfxMixerLoop() },
          "ShroomAutoSfx",
        )
        .apply {
          isDaemon = true
          start()
        }
  }

  fun isMuted(): Boolean = muted.get()

  fun setMuted(isMuted: Boolean) {
    muted.set(isMuted)
  }

  fun setPaused(isPaused: Boolean) {
    paused.set(isPaused)
  }

  fun setStarPower(active: Boolean) {
    starMode.set(active)
  }

  fun release() {
    released.set(true)
    bgmThread.interrupt()
    sfxThread.interrupt()
  }

  override fun onJump() = enqueueSfx(jumpPcm)
  override fun onShoot() = enqueueSfx(shootPcm)
  override fun onCoin() = enqueueSfx(coinPcm)
  override fun onPowerUp() = enqueueSfx(powerUpPcm)
  override fun onEnemyDefeat() = enqueueSfx(defeatPcm)
  override fun onBlockHit() = enqueueSfx(blockHitPcm)
  override fun onCarEnter() = enqueueSfx(carEnterPcm)
  override fun onDamage() = enqueueSfx(damagePcm)
  override fun onWantedUp() = enqueueSfx(wantedPcm)
  override fun onVictory() = enqueueSfx(victoryPcm)
  override fun onBusted() = enqueueSfx(bustedPcm)
  override fun onMissileLaunch() = enqueueSfx(missilePcm)
  override fun onExplosion() = enqueueSfx(explosionPcm)

  private fun enqueueSfx(pcm: ShortArray) {
    if (muted.get() || released.get()) return
    synchronized(voiceLock) {
      if (activeVoices.size >= 8) {
        activeVoices.removeAt(0)
      }
      activeVoices.add(ActiveVoice(pcm))
    }
  }

  private fun runSfxMixerLoop() {
    var track: AudioTrack? = null
    try {
      val minBufBytes =
        AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (minBufBytes <= 0) return
      val chunkSamples = 256
      val bufferBytes = minBufBytes.coerceAtLeast(chunkSamples * 2 * 2)
      track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(bufferBytes)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      track.play()

      val mixChunk = ShortArray(chunkSamples)
      val silenceChunk = ShortArray(chunkSamples)

      while (!released.get() && !Thread.currentThread().isInterrupted) {
        if (muted.get()) {
          synchronized(voiceLock) { activeVoices.clear() }
          track.write(silenceChunk, 0, silenceChunk.size)
          Thread.sleep(16)
          continue
        }

        var hasVoices: Boolean
        synchronized(voiceLock) {
          hasVoices = activeVoices.isNotEmpty()
          if (hasVoices) {
            for (i in 0 until chunkSamples) {
              var sum = 0
              val it = activeVoices.iterator()
              while (it.hasNext()) {
                val v = it.next()
                if (v.pos < v.pcm.size) {
                  sum += v.pcm[v.pos++]
                } else {
                  it.remove()
                }
              }
              mixChunk[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            activeVoices.removeAll { it.pos >= it.pcm.size }
          }
        }

        if (hasVoices) {
          track.write(mixChunk, 0, mixChunk.size)
        } else {
          track.write(silenceChunk, 0, silenceChunk.size)
          Thread.sleep(8)
        }
      }
    } catch (_: InterruptedException) {
      // Expected on release
    } catch (_: Throwable) {
      // Ignore hardware audio unavailability on headless runners
    } finally {
      try {
        track?.stop()
      } catch (_: Throwable) {
      }
      try {
        track?.release()
      } catch (_: Throwable) {
      }
    }
  }

  private fun runBackgroundMusicLoop() {
    var track: AudioTrack? = null
    try {
      val minBufBytes =
        AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (minBufBytes <= 0) return
      val bufferBytes = minBufBytes.coerceAtLeast(4096)
      track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(bufferBytes)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()

      track.play()

      val melody: List<Double>
      val bass: List<Double>
      val baseStepSamples: Int
      when (worldTheme) {
        WorldTheme.TOADSTOOL_STRIP -> {
          // Upbeat Mario-style highway groove
          melody =
            listOf(
              659.25, 659.25, 0.0, 659.25, 0.0, 523.25, 659.25, 0.0,
              783.99, 0.0, 0.0, 0.0, 392.00, 0.0, 0.0, 0.0,
              523.25, 0.0, 0.0, 392.00, 0.0, 0.0, 329.63, 0.0,
              440.00, 0.0, 493.88, 0.0, 466.16, 440.00, 0.0, 392.00,
            )
          bass =
            listOf(
              130.81, 0.0, 196.00, 0.0, 130.81, 0.0, 196.00, 0.0,
              196.00, 0.0, 146.83, 0.0, 196.00, 0.0, 123.47, 0.0,
              130.81, 0.0, 164.81, 0.0, 196.00, 0.0, 164.81, 0.0,
              174.61, 0.0, 196.00, 0.0, 146.83, 0.0, 196.00, 0.0,
            )
          baseStepSamples = (sampleRate * 0.125).toInt()
        }
        WorldTheme.KOOPA_VICE_HWY -> {
          // Neon Vice highway synth-arp
          melody =
            listOf(
              440.00, 523.25, 659.25, 880.00, 783.99, 659.25, 523.25, 0.0,
              392.00, 493.88, 587.33, 783.99, 659.25, 587.33, 493.88, 0.0,
              349.23, 440.00, 523.25, 698.46, 659.25, 523.25, 440.00, 0.0,
              329.63, 415.30, 493.88, 659.25, 587.33, 523.25, 493.88, 415.30,
            )
          bass =
            listOf(
              110.00, 110.00, 220.00, 110.00, 110.00, 110.00, 220.00, 110.00,
              98.00, 98.00, 196.00, 98.00, 98.00, 98.00, 196.00, 98.00,
              87.31, 87.31, 174.61, 87.31, 87.31, 87.31, 174.61, 87.31,
              82.41, 82.41, 164.81, 82.41, 98.00, 98.00, 196.00, 98.00,
            )
          baseStepSamples = (sampleRate * 0.115).toInt()
        }
        WorldTheme.BOWSER_BLVD -> {
          // Intense Bowser fortress chase motif
          melody =
            listOf(
              293.66, 349.23, 440.00, 466.16, 440.00, 349.23, 329.63, 0.0,
              293.66, 349.23, 523.25, 466.16, 440.00, 392.00, 349.23, 0.0,
              587.33, 0.0, 523.25, 0.0, 466.16, 440.00, 392.00, 0.0,
              440.00, 466.16, 523.25, 587.33, 659.25, 587.33, 523.25, 440.00,
            )
          bass =
            listOf(
              73.42, 146.83, 73.42, 146.83, 73.42, 146.83, 82.41, 164.81,
              73.42, 146.83, 73.42, 146.83, 87.31, 174.61, 98.00, 196.00,
              116.54, 116.54, 130.81, 130.81, 146.83, 146.83, 98.00, 98.00,
              110.00, 110.00, 123.47, 123.47, 130.81, 146.83, 164.81, 110.00,
            )
          baseStepSamples = (sampleRate * 0.108).toInt()
        }
      }

      val starMelody =
        listOf(
          523.25, 523.25, 523.25, 440.0, 523.25, 0.0, 523.25, 440.0,
          493.88, 493.88, 493.88, 392.0, 493.88, 0.0, 493.88, 392.0,
        )

      var stepIdx = 0
      var leadPhase = 0.0
      var bassPhase = 0.0
      val silenceChunk = ShortArray(1024)

      while (!released.get() && !Thread.currentThread().isInterrupted) {
        if (muted.get() || paused.get()) {
          track.write(silenceChunk, 0, silenceChunk.size)
          Thread.sleep(35)
          continue
        }

        val isStar = starMode.get()
        val activeMelody = if (isStar) starMelody else melody
        val leadHz = activeMelody[stepIdx % activeMelody.size]
        val bassHz = bass[stepIdx % bass.size]
        val stepSamples = if (isStar) (baseStepSamples * 0.72).toInt() else baseStepSamples
        val chunk = ShortArray(stepSamples)

        for (i in 0 until stepSamples) {
          val env =
            when {
              i < 120 -> i / 120.0
              i > stepSamples - 320 -> ((stepSamples - i) / 320.0).coerceAtLeast(0.0)
              else -> 1.0
            }
          var sample = 0.0
          if (leadHz > 0.0) {
            leadPhase += leadHz / sampleRate
            if (leadPhase >= 1.0) leadPhase -= 1.0
            val sq = if (leadPhase < 0.35) 1.0 else -1.0
            sample += sq * 0.11 * env
          }
          if (bassHz > 0.0) {
            bassPhase += bassHz / sampleRate
            if (bassPhase >= 1.0) bassPhase -= 1.0
            // Triangle wave bass for warm NES bottom-end
            val tri = 1.0 - 4.0 * kotlin.math.abs(bassPhase - 0.5)
            sample += tri * 0.13
          }
          // Subtle chiptune snare/hat tick on even steps
          if (i < 220 && (stepIdx % 2 == 1)) {
            val tick = (((i * 1103515245 + stepIdx) and 0xFF) / 128.0 - 1.0) * (1.0 - i / 220.0)
            sample += tick * 0.04
          }
          chunk[i] = (sample.coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
        }

        track.write(chunk, 0, chunk.size)
        stepIdx++
      }
    } catch (_: InterruptedException) {
      // Expected on release
    } catch (_: Throwable) {
      // Ignore audio hardware issues
    } finally {
      try {
        track?.stop()
      } catch (_: Throwable) {
      }
      try {
        track?.release()
      } catch (_: Throwable) {
      }
    }
  }

  private enum class Wave {
    SQUARE,
    TRIANGLE,
    SAW,
    NOISE_SQUARE,
  }

  private fun synthSweep(
    startHz: Double,
    endHz: Double,
    durationMs: Int,
    wave: Wave,
    vol: Double,
  ): ShortArray {
    val count = (sampleRate * durationMs) / 1000
    val out = ShortArray(count)
    var phase = 0.0
    var seed = 1337
    for (i in 0 until count) {
      val t = i.toDouble() / count.coerceAtLeast(1)
      val hz = startHz + (endHz - startHz) * t
      phase += hz / sampleRate
      if (phase >= 1.0) phase -= 1.0
      val raw =
        when (wave) {
          Wave.SQUARE -> if (phase < 0.5) 1.0 else -1.0
          Wave.TRIANGLE -> 1.0 - 4.0 * kotlin.math.abs(phase - 0.5)
          Wave.SAW -> 2.0 * phase - 1.0
          Wave.NOISE_SQUARE -> {
            seed = seed * 1103515245 + 12345
            val noise = ((seed ushr 16) and 0x7FFF) / 16384.0 - 1.0
            (if (phase < 0.5) 0.7 else -0.7) + noise * 0.35
          }
        }
      val env = (1.0 - t * 0.65) * sin((1.0 - t * 0.5) * PI * 0.5)
      out[i] = ((raw * vol * env).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
    }
    return out
  }

  private fun synthNotes(
    notes: List<Pair<Double, Int>>,
    wave: Wave,
    vol: Double,
  ): ShortArray {
    val totalSamples = notes.sumOf { (sampleRate * it.second) / 1000 }
    val out = ShortArray(totalSamples)
    var idx = 0
    var phase = 0.0
    for ((hz, ms) in notes) {
      val len = (sampleRate * ms) / 1000
      for (i in 0 until len) {
        val t = i.toDouble() / len.coerceAtLeast(1)
        phase += hz / sampleRate
        if (phase >= 1.0) phase -= 1.0
        val raw =
          when (wave) {
            Wave.SQUARE -> if (phase < 0.45) 1.0 else -1.0
            Wave.SAW -> 2.0 * phase - 1.0
            Wave.TRIANGLE -> 1.0 - 4.0 * kotlin.math.abs(phase - 0.5)
            Wave.NOISE_SQUARE -> if (phase < 0.5) 1.0 else -1.0
          }
        val env = if (t > 0.8) (1.0 - t) / 0.2 else 1.0
        if (idx < out.size) {
          out[idx++] = ((raw * vol * env).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
      }
    }
    return out
  }
}
