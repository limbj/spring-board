package local;

import java.io.File;
import java.nio.file.Path;

public final class DevServer {
    private DevServer() { }
    public static void main(String[] arguments) throws Exception {
        Path project = Path.of(arguments.length > 0 ? arguments[0] : ".").toAbsolutePath().normalize();
        int port = arguments.length > 1 ? Integer.parseInt(arguments[1]) : 8080;
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
        if (System.getProperty("springboard.dataDir") == null && System.getenv("SPRING_BOARD_DATA_DIR") == null) {
            System.setProperty("springboard.dataDir", project.resolve("data").toString());
        }
        if (System.getProperty("springboard.uploadDir") == null && System.getenv("SPRING_BOARD_UPLOAD_DIR") == null) {
            System.setProperty("springboard.uploadDir", project.resolve("data/uploads").toString());
        }
        File war = project.resolve("local/spring-board.war").toFile();
        if (!war.isFile()) war = project.resolve("target/spring-board.war").toFile();
        BoardServer server = BoardServer.start(war, project.resolve("runtime").toFile(), port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (Exception ignored) { }
        }, "spring-board-shutdown"));
        System.out.println("Spring Board is ready: http://127.0.0.1:" + server.getPort() + "/spring-board/");
        System.out.println("Press Ctrl+C to stop.");
        server.await();
    }
}
