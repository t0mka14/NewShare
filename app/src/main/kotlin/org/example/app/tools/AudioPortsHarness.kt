package org.example.app.tools

import org.example.app.domain.audio.MicNames
import org.example.app.infrastructure.audio.JvmAudioInputGainControl
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.CompoundControl
import javax.sound.sampled.Control
import javax.sound.sampled.FloatControl
import javax.sound.sampled.Mixer
import javax.sound.sampled.Port
import javax.sound.sampled.TargetDataLine

/**
 * Diagnostic for the microphone-level feature (§13 decision 44), meant to be run on the
 * deployment machine: `./gradlew :app:listAudioPorts` lists every Java Sound mixer with its
 * name length — a name of exactly 31 characters on Windows is truncated (JDK-7116070) — and,
 * for port mixers, each port with its controls. `-Pset="<device name>:<percent>"` additionally
 * runs [JvmAudioInputGainControl] against that name (the capture device's name as Settings shows
 * it, or the port mixer's name without `Port `) and prints the result, so the mechanism can be
 * checked against the Windows sound panel before trusting it in a session.
 */
fun main(args: Array<String>) {
    println("Java ${System.getProperty("java.version")} on ${System.getProperty("os.name")}")
    println()
    for (info in AudioSystem.getMixerInfo()) describeMixer(info)

    val setIndex = args.indexOf("--set")
    if (setIndex >= 0 && args.size >= setIndex + 3) {
        val name = args[setIndex + 1]
        val percent = args[setIndex + 2].toIntOrNull()
        if (percent == null) {
            println("percent must be an integer 0..100, was '${args[setIndex + 2]}'")
            return
        }
        val control = JvmAudioInputGainControl()
        println()
        println("Setting '$name' to $percent ...")
        println("  result: ${control.setInputGain(name, percent)}")
        println("  read back: ${control.readInputGain(name)}")
    }
}

private fun describeMixer(info: Mixer.Info) {
    val truncated = if (info.name.length == MicNames.WINDOWS_TRUNCATED_NAME_LENGTH) "  <-- 31 chars, truncated on Windows (JDK-7116070)" else ""
    println("MIXER '${info.name}' (len=${info.name.length})$truncated")
    println("      vendor='${info.vendor}' version='${info.version}' description='${info.description}'")
    val mixer = try {
        AudioSystem.getMixer(info)
    } catch (e: Exception) {
        println("      (cannot open: $e)")
        return
    }
    val hasCapture = mixer.targetLineInfo.any { TargetDataLine::class.java.isAssignableFrom(it.lineClass) }
    val inputPorts = mixer.sourceLineInfo.filterIsInstance<Port.Info>()
    val outputPorts = mixer.targetLineInfo.filterIsInstance<Port.Info>()
    println("      capture line: $hasCapture, input ports: ${inputPorts.size}, output ports: ${outputPorts.size}")
    for (portInfo in inputPorts) describePort(mixer, portInfo, "INPUT ")
    for (portInfo in outputPorts) describePort(mixer, portInfo, "OUTPUT")
}

private fun describePort(mixer: Mixer, portInfo: Port.Info, kind: String) {
    println("      $kind port '${portInfo.name}' isSource=${portInfo.isSource}")
    try {
        val port = mixer.getLine(portInfo) as Port
        port.open()
        try {
            port.controls.forEach { describeControl(it, "         ") }
        } finally {
            port.close()
        }
    } catch (e: Exception) {
        println("         (cannot open: $e)")
    }
}

private fun describeControl(control: Control, indent: String) {
    when (control) {
        is CompoundControl -> {
            println("$indent${control.type} {")
            control.memberControls.forEach { describeControl(it, "$indent  ") }
            println("$indent}")
        }
        is FloatControl ->
            println("$indent${control.type}: value=${control.value} min=${control.minimum} max=${control.maximum} units='${control.units}'")
        else -> println("$indent${control.type} (${control.javaClass.simpleName})")
    }
}
