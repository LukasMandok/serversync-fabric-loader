package serversync.fabric.loader;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerSyncLoader implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");

    // create new serverTread for ServerSync
    private Thread serverThread;

    @Override
    public void onInitialize() {
        // on Server starting, start ServerSync in a new thread
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {

            // find the serversync jar file in the minecraft folder
            try (Stream<Path> fileStream = Files.list(Paths.get(""))) {
                List<Path> serversync = fileStream
                    .parallel()
                    .filter(f -> f.getFileName().toString().matches("serversync-(\\d+\\.\\d+\\.\\d+)(?:-(\\w+)|-(\\w+\\.\\d+))*\\.jar"))
                    .collect(Collectors.toList());
                
                if (serversync.size() < 1) {
                    LOGGER.error("Failed to find ServerSync, have you added it to your minecraft folder?");
                    return;
                }

                if (serversync.size() > 1) {
                    LOGGER.error(String.format(
                        "Found multiple versions of ServerSync: %s, remove the excess versions.",
                        serversync
                    ));    
                    return;
                }

                // We have enforced that serversync exists already so this hackery is less awful than usual.
                // Loads our serversync.jar into a class loader and reflectively calls the start server sequence

                URLClassLoader modClassLoader = new URLClassLoader(new URL[]{serversync.get(0).toUri().toURL()}, this.getClass().getClassLoader());
                Class<?> serversyncClass = Class.forName("com.superzanti.serversync.ServerSync", true, modClassLoader);
                Object serversyncInstance = serversyncClass.getDeclaredConstructor().newInstance();
            
                Field rootDir = serversyncClass.getDeclaredField("rootDir");
                rootDir.set(null, Paths.get(""));

                Method runServer = serversyncClass.getDeclaredMethod("runInServerMode");
                runServer.setAccessible(true);
                LOGGER.info("Starting ServerSync server via fabric loader.");
                serverThread = (Thread) runServer.invoke(serversyncInstance);

            // TODO: add more elaborate error handling    
            } catch (Exception e) {
                LOGGER.error("Failed to load ServerSync");
                e.printStackTrace();
                return;
            }
        });

        // on Server stopping, interrupt ServerSync thread
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("ServerLifecycleEvents.SERVER_STOPPING");
            serverThread.interrupt();
        });
    }
    
}
