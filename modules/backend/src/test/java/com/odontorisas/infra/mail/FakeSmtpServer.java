package com.odontorisas.infra.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor SMTP mínimo para tests ({@code update-project-foundation-local-mail} tasks 1.4): atiende una sola
 * conexión en un puerto dinámico de loopback, con un protocolo determinista, y registra el verbo de cada comando
 * recibido. Anuncia {@code STARTTLS} solo si se le pide, y nunca lo negocia: sirve para comprobar si el cliente
 * lo exige o lo intenta. Rechaza {@code AUTH}.
 */
final class FakeSmtpServer implements AutoCloseable {

    private static final int SOCKET_TIMEOUT_MILLIS = 2_000;

    private final ServerSocket socket;
    private final boolean advertiseStartTls;
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private final Thread worker;

    FakeSmtpServer(boolean advertiseStartTls) throws IOException {
        this.advertiseStartTls = advertiseStartTls;
        this.socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        this.socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        this.worker = new Thread(this::serveOneConnection, "fake-smtp");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    int port() {
        return socket.getLocalPort();
    }

    /** Verbos recibidos en orden ({@code EHLO}, {@code MAIL}, {@code DATA}, ...). */
    List<String> commands() {
        return List.copyOf(commands);
    }

    private void serveOneConnection() {
        try (Socket client = socket.accept()) {
            client.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            Writer out = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.US_ASCII);
            reply(out, "220 fake ESMTP");
            String line;
            while ((line = in.readLine()) != null) {
                String verb = line.split(" ", 2)[0].toUpperCase(Locale.ROOT);
                commands.add(verb);
                switch (verb) {
                    case "EHLO" -> reply(out, advertiseStartTls
                        ? "250-fake\r\n250-STARTTLS\r\n250 OK"
                        : "250-fake\r\n250 OK");
                    case "HELO", "MAIL", "RCPT", "RSET", "NOOP" -> reply(out, "250 OK");
                    case "DATA" -> {
                        reply(out, "354 End data with <CR><LF>.<CR><LF>");
                        String body;
                        while ((body = in.readLine()) != null && !body.equals(".")) {
                            // descarta el mensaje
                        }
                        reply(out, "250 OK queued");
                    }
                    case "STARTTLS" -> reply(out, "454 TLS not available");
                    case "AUTH" -> reply(out, "535 Authentication not supported");
                    case "QUIT" -> {
                        reply(out, "221 Bye");
                        return;
                    }
                    default -> reply(out, "502 Command not implemented");
                }
            }
        } catch (IOException ignored) {
            // timeout o cierre del cliente: el test decide por los comandos registrados
        }
    }

    private static void reply(Writer out, String response) throws IOException {
        out.write(response + "\r\n");
        out.flush();
    }

    @Override
    public void close() throws Exception {
        try {
            socket.close();
        } finally {
            worker.join(SOCKET_TIMEOUT_MILLIS * 2L);
        }
    }
}
