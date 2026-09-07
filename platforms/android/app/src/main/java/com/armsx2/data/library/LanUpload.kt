package com.armsx2.data.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Sending a game from a computer, over the local network.
 *
 * Getting a 250 MB archive onto a phone is the least pleasant part of setting this emulator up: a
 * cable, a file manager, and a guess about which folder. So the app puts up a small web page on
 * the local network instead — open the address on the computer, drop the archive on it, and the
 * installer that already exists takes it from there.
 *
 * Deliberately small and deliberately temporary. It answers exactly two requests — the page, and
 * one upload — accepts one connection at a time, and only runs while the player has it switched
 * on. There is no browsing, no listing, and nothing to read: an upload writes to a staging name in
 * the ROM folder and nothing else on the device is reachable through it.
 *
 * The upload is a plain PUT with the file as the whole body, rather than a multipart form. That is
 * a page-side choice that removes the only fiddly part of speaking HTTP by hand: with
 * `Content-Length` bytes to copy and no boundaries to find, the server is a loop.
 */
object LanUpload {

    private const val TAG = "LanUpload"
    private const val PORT = 8246

    /** The address to type on the computer, or null while stopped. */
    val address = mutableStateOf<String?>(null)

    /** What has arrived, newest last. Shown in the sheet as it happens. */
    val log = mutableStateListOf<String>()

    /** Bytes received for the upload in flight, and its name. */
    val receivingName = mutableStateOf<String?>(null)
    val received = mutableStateOf(0L)
    val expected = mutableStateOf(0L)

    @Volatile private var server: ServerSocket? = null
    @Volatile private var worker: Thread? = null

    val running: Boolean get() = server != null

    /**
     * Start listening. Returns the address, or null with a reason in [log].
     *
     * [onInstalled] fires after each archive is installed, so the library behind can refresh.
     */
    fun start(context: Context, onInstalled: () -> Unit): String? {
        if (running) return address.value
        val roms = ArcadeZipInstall.romsDir()
        if (roms == null) {
            log += "Nenhuma pasta de ROMs com caminho de arquivo real e gravável."
            return null
        }
        val addresses = candidates()
        val ip = addresses.firstOrNull()
        if (ip == null) {
            log += "Este aparelho não está numa rede local (Wi-Fi desligado?)."
            return null
        }
        unreachable.value = isEmulatorOnly(addresses)
        val socket = runCatching { ServerSocket(PORT) }.getOrElse {
            log += "Não foi possível abrir a porta $PORT: ${it.message}"
            return null
        }
        server = socket
        val url = "http://$ip:$PORT"
        address.value = url
        log += "Aberto em $url"
        worker = Thread {
            while (true) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { serve(context, client, onInstalled) }
                    .onFailure { Log.w(TAG, "conexão falhou: ${it.message}") }
                runCatching { client.close() }
            }
        }.apply { isDaemon = true; start() }
        return url
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        worker = null
        address.value = null
        receivingName.value = null
        unreachable.value = false
    }

    // ---- one connection ------------------------------------------------------

    private fun serve(context: Context, client: Socket, onInstalled: () -> Unit) {
        val input = client.getInputStream()
        val request = readLine(input) ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }
        val parts = request.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty()

        when {
            method == "GET" && path.startsWith("/upload") ->
                respond(client, 405, "text/plain", "use PUT")
            method == "GET" -> respond(client, 200, "text/html; charset=utf-8", PAGE)
            method == "PUT" && path.startsWith("/upload") -> {
                val name = fileNameFrom(path)
                val length = headers["content-length"]?.toLongOrNull() ?: 0L
                if (name == null || length <= 0L) {
                    respond(client, 400, "text/plain", "nome ou tamanho inválido")
                    return
                }
                val result = receive(context, input, name, length, onInstalled)
                respond(client, 200, "text/plain", result)
            }
            else -> respond(client, 404, "text/plain", "não encontrado")
        }
    }

    /**
     * The uploaded name, stripped to a bare filename.
     *
     * A browser sends whatever it was given, and a name carrying a path would write outside the
     * ROM folder. Everything up to the last separator goes.
     */
    internal fun fileNameFrom(path: String): String? {
        val query = path.substringAfter('?', "")
        val raw = query.split('&')
            .firstOrNull { it.startsWith("name=") }
            ?.removePrefix("name=")
            ?: return null
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        val bare = decoded.replace('\\', '/').substringAfterLast('/').trim()
        if (bare.isEmpty() || bare == "." || bare == "..") return null
        return bare
    }

    private fun receive(
        context: Context,
        input: InputStream,
        name: String,
        length: Long,
        onInstalled: () -> Unit,
    ): String {
        val roms = ArcadeZipInstall.romsDir() ?: return "sem pasta de ROMs"
        val staging = File(roms, ".$name.part")
        receivingName.value = name
        received.value = 0L
        expected.value = length

        val copied = runCatching {
            BufferedOutputStream(staging.outputStream()).use { out ->
                val buffer = ByteArray(1 shl 16)
                var left = length
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                    left -= n
                    received.value = length - left
                }
                left == 0L
            }
        }.getOrDefault(false)

        receivingName.value = null
        if (!copied) {
            runCatching { staging.delete() }
            log += "$name: transferência incompleta"
            return "transferência incompleta"
        }

        val archive = File(roms, name)
        runCatching { archive.delete() }
        if (!staging.renameTo(archive)) {
            runCatching { staging.delete() }
            log += "$name: não deu para gravar na pasta de ROMs"
            return "não deu para gravar"
        }

        // Straight into the installer that already exists, so an upload behaves exactly like
        // picking the same file from storage -- including every refusal it knows how to give.
        val outcome = when {
            ArcadeZipInstall.unsupported(name) -> "não dá para abrir $name"
            else -> when (val look = ArcadeZipInstall.inspect(context, Uri.fromFile(archive), name)) {
                is ArcadeZipInstall.Look.Refused -> look.why
                is ArcadeZipInstall.Look.Ready -> {
                    val problem = ArcadeZipInstall.install(context, Uri.fromFile(archive), look.preview) {}
                    problem ?: "${look.preview.gameId} instalado"
                }
            }
        }
        // The archive was only ever a delivery: the game is unpacked by now, and leaving a copy
        // behind would quietly double what a collection costs on the device.
        runCatching { archive.delete() }
        log += "$name: $outcome"
        onInstalled()
        return outcome
    }

    // ---- plumbing ------------------------------------------------------------

    /** Read one CRLF-terminated line without buffering past it, which the body needs intact. */
    private fun readLine(input: InputStream): String? {
        val out = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (out.isEmpty()) null else out.toString()
            if (c == '\n'.code) return out.toString().removeSuffix("\r")
            out.append(c.toChar())
        }
    }

    private fun respond(client: Socket, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status ${if (status == 200) "OK" else "ERR"}\r\n")
            append("Content-Type: $type\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    /**
     * The address a computer on the same network would use, or null when there is none.
     *
     * Wi-Fi first, then anything else site-local. A phone can have several at once -- a VPN, a
     * hotspot, USB tethering -- and the first one the system happens to list is not necessarily
     * the one the computer is on.
     */
    private fun localAddress(): String? = candidates().firstOrNull()

    private fun candidates(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { nif -> nif.inetAddresses.toList().map { nif to it } }
            .filter { (_, addr) -> addr is Inet4Address && addr.isSiteLocalAddress }
            .mapNotNull { (_, addr) -> addr.hostAddress }
    }.getOrDefault(emptyList())

    /**
     * True when the only address this device has is an emulator's private NAT.
     *
     * 10.0.2.15 with a 10.0.2.2 gateway is the address every Android emulator hands its guest,
     * and that network exists only inside the emulator's own virtual router -- the machine
     * running it cannot reach it, however correct the server is. Worth saying out loud, because
     * from the outside it looks exactly like a broken feature.
     */
    internal fun isEmulatorOnly(addresses: List<String>): Boolean =
        addresses.isNotEmpty() && addresses.all { it.startsWith("10.0.2.") }

    /** Set when the address shown cannot be reached from another machine. */
    val unreachable = mutableStateOf(false)

    /** The page the computer sees. One file input, one drop target, one progress bar. */
    private val PAGE = """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Enviar jogo</title><style>
        body{background:#0b0b0d;color:#f5f5f7;font:16px/1.5 system-ui,sans-serif;margin:0;
             display:flex;min-height:100vh;align-items:center;justify-content:center}
        .card{width:min(560px,92vw);padding:28px;border:1px solid #ffffff20;border-radius:22px;
              background:#ffffff08}
        h1{font-size:20px;margin:0 0 6px}p{color:#a8a8b3;margin:0 0 20px}
        #drop{border:2px dashed #ffffff33;border-radius:16px;padding:44px 18px;text-align:center;
              color:#a8a8b3}
        #drop.over{border-color:#c8202f;color:#f5f5f7}
        progress{width:100%;height:10px;margin-top:18px}
        #log{margin-top:18px;font-size:14px;color:#a8a8b3;white-space:pre-line}
        </style></head><body><div class="card">
        <h1>Enviar jogo para o emulador</h1>
        <p>Arraste um .zip ou .7z aqui, ou escolha um arquivo.</p>
        <div id="drop">solte o arquivo aqui<br><br><input type="file" id="f"></div>
        <progress id="p" value="0" max="100" hidden></progress>
        <div id="log"></div>
        </div><script>
        const drop=document.getElementById('drop'),f=document.getElementById('f'),
              p=document.getElementById('p'),log=document.getElementById('log');
        function send(file){
          if(!file)return;
          p.hidden=false;p.value=0;log.textContent='enviando '+file.name+'…';
          const x=new XMLHttpRequest();
          x.open('PUT','/upload?name='+encodeURIComponent(file.name));
          x.upload.onprogress=e=>{if(e.lengthComputable)p.value=e.loaded/e.total*100};
          x.onload=()=>{p.hidden=true;log.textContent=file.name+': '+x.responseText};
          x.onerror=()=>{p.hidden=true;log.textContent='falhou a conexão'};
          x.send(file);
        }
        f.onchange=()=>send(f.files[0]);
        drop.ondragover=e=>{e.preventDefault();drop.classList.add('over')};
        drop.ondragleave=()=>drop.classList.remove('over');
        drop.ondrop=e=>{e.preventDefault();drop.classList.remove('over');send(e.dataTransfer.files[0])};
        </script></body></html>
    """.trimIndent()
}
