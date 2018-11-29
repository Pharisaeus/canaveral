package pl.codewise.samples.spring.it.configuration;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BinaryMockServer implements AutoCloseable {

    private final ExecutorService executorService;
    private final List<Long> numbersStorage = new ArrayList<>(1000);
    private ServerSocket serverSocket;
    private int port;

    public BinaryMockServer(int port) throws IOException {
        this.port = port;
        serverSocket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress());
        executorService = Executors.newFixedThreadPool(10,
                new ThreadFactoryBuilder()
                        .setDaemon(false)
                        .setNameFormat("echo-server-%d")
                        .build());
    }

    @Override
    public void close() throws IOException, InterruptedException {
        serverSocket.close();
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);
        printMsg("Server closed");
    }

    public void start() {
        printMsg("Starting server on port: " + port);
        executorService.submit(() -> {
            try {
                acceptConnections();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void reset() {
        this.numbersStorage.clear();
    }

    public List<Long> getStoredNumbers() {
        return new ArrayList<>(numbersStorage);
    }

    int getPort() {
        return port;
    }

    private void acceptConnections() throws IOException {
        Socket socket;
        while (!serverSocket.isClosed()) {
            socket = serverSocket.accept();
            printMsg("Accepted new connection from " + socket.getRemoteSocketAddress());
            submitTask(socket);
        }
        printMsg("Accepting new connection finished");
    }

    private void submitTask(Socket socket) {
        executorService.submit(() -> doResponse(socket));
    }

    private void doResponse(Socket socket) {
        printMsg("Starting making response to the client: " + socket.getRemoteSocketAddress());

        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {

            while (in.available() > 0) {
                long number = in.readLong();
                printMsg("Received number: " + number);
                numbersStorage.add(number);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            printMsg("Finished reading from client " + socket.getRemoteSocketAddress());
        }
    }

    private void printMsg(String message) {
        System.out.println(Thread.currentThread().getName() + "| " + LocalDateTime.now() + "| " + message);
    }
}
