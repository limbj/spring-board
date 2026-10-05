package local;

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.apache.tomcat.util.scan.StandardJarScanner;

/** Shared by real-WAR integration tests and the portable local launcher. */
public final class BoardServer implements AutoCloseable {
    private final Tomcat tomcat;
    private BoardServer(Tomcat tomcat) { this.tomcat = tomcat; }

    public static BoardServer start(File war, File runtime, int port) throws Exception {
        if (!war.isFile()) throw new IllegalArgumentException("Build spring-board.war before starting the server");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be 0..65535");
        if (!runtime.isDirectory() && !runtime.mkdirs()) throw new IllegalStateException("Cannot create runtime directory");
        File webapps = new File(runtime, "webapps");
        if (!webapps.isDirectory() && !webapps.mkdirs()) throw new IllegalStateException("Cannot create web application directory");
        Tomcat server = new Tomcat();
        server.setBaseDir(runtime.getCanonicalPath());
        server.setPort(port);
        server.getConnector().setProperty("address", "127.0.0.1");
        server.getConnector().setMaxPostSize(62_914_560);
        Context context = server.addWebapp("/spring-board", war.getCanonicalPath());
        context.setParentClassLoader(BoardServer.class.getClassLoader());
        context.setUseHttpOnly(true);
        Rfc6265CookieProcessor cookies = new Rfc6265CookieProcessor();
        cookies.setSameSiteCookies("lax");
        context.setCookieProcessor(cookies);
        StandardJarScanner scanner = new StandardJarScanner();
        scanner.setScanManifest(false);
        scanner.setScanClassPath(false);
        context.setJarScanner(scanner);
        try {
            server.start();
            if (!context.getState().isAvailable()) throw new IllegalStateException("Spring Board application did not start");
            return new BoardServer(server);
        } catch (Exception exception) {
            try { server.stop(); server.destroy(); } catch (LifecycleException ignored) { }
            throw exception;
        }
    }
    public int getPort() { return tomcat.getConnector().getLocalPort(); }
    public void await() { tomcat.getServer().await(); }
    @Override
    public void close() throws LifecycleException { tomcat.stop(); tomcat.destroy(); }
}
