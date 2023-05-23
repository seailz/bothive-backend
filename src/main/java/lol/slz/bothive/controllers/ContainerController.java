package lol.slz.bothive.controllers;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.servlet.http.HttpServletRequest;
import lol.slz.bothive.Bothive;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.QueryParam;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

@RestController
@CrossOrigin(origins = "*")
public class ContainerController {

    /**
     * Allows the user to modify the status of the bot.
     * This could include killing, restarting, stopping, or starting the bot.
     * It's defined by the "action" param in the request body.
     * The action param is an integer, and the following actions are defined:
     * 0 - Start
     * 1 - Stop
     * 2 - Restart
     * 3 - Kill
     */
    @RequestMapping(value = "/status", method = RequestMethod.POST)
    public ResponseEntity<String> updateStatus(HttpServletRequest req, @RequestHeader("Authorization") String accessToken, @RequestParam("bot") String botId) throws IOException, ExecutionException, InterruptedException {
        String idToken = accessToken.replace("Bearer ", "");
        FirebaseToken decodedToken = null;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new JSONObject()
                            .put("message", "Invalid token")
                            .put("error", HttpStatus.UNAUTHORIZED.value())
                            .toString()
            );
        }
        String userId = decodedToken.getUid();
        Firestore db = FirestoreClient.getFirestore();
        System.out.println(userId);
        ApiFuture<QuerySnapshot> future = db.collection("bots").whereEqualTo("userId", userId).get();

        boolean found = false;
        DocumentSnapshot bot = null;
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (DocumentSnapshot document : documents) {
            if (document.getId().equals(botId)) {
                found = true;
                bot = document;
                break;
            }
        }

        if (!found) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new JSONObject()
                            .put("message", "Bot not found")
                            .put("error", HttpStatus.NOT_FOUND.value())
                            .toString()
            );
        }

        JSONObject body = new JSONObject(req.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual));

        if (!body.has("action") || !(body.get("action") instanceof Integer)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject()
                            .put("message", "Missing action")
                            .put("error", HttpStatus.BAD_REQUEST.value())
                            .toString()
            );
        }
        int action = body.getInt("action");
        Status status = Status.fromValue(action);

        if (status == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject()
                            .put("message", "Invalid action")
                            .put("error", HttpStatus.BAD_REQUEST.value())
                            .toString()
            );
        }

        switch (status) {
            case KILL -> {
                // First check if the bot is running
                if (!bot.contains("container_id") || bot.getString("container_id") == null || bot.getString("container_id").equals("")) {
                    return ResponseEntity.status(409).body(
                            new JSONObject()
                                    .put("message", "Bot is not running")
                                    .put("error", 409)
                                    .toString()
                    );
                }

                // If container ID is found, we need to first check if that is still a valid container.
                // If not, then we need to return 409.
                // If it is, then we need to return the console output.

                List<Container> containers = Bothive.client.listContainersCmd().exec();
                boolean containerFound = false;
                Container containerFoundObj = null;
                for (Container container : containers) {
                    if (container.getId().equals(bot.getString("container_id"))) {
                        containerFound = true;
                        containerFoundObj = container;
                        break;
                    }
                }

                if (!containerFound) {
                    return ResponseEntity.status(409).body(
                            new JSONObject()
                                    .put("message", "Bot is not running")
                                    .put("error", 409)
                                    .toString()
                    );
                }

                // If the container is found, then we need to kill it.
                Bothive.client.killContainerCmd(containerFoundObj.getId()).exec();
                return ResponseEntity.ok(
                        new JSONObject()
                                .put("message", "Bot killed")
                                .put("code", 200)
                                .toString()
                );
            }
            case STOP -> {
                // First check if the bot is running
                if (!bot.contains("container_id") || bot.getString("container_id") == null || bot.getString("container_id").equals("")) {
                    return ResponseEntity.status(409).body(
                            new JSONObject()
                                    .put("message", "Bot is not running")
                                    .put("error", 409)
                                    .toString()
                    );
                }

                // If container ID is found, we need to first check if that is still a valid container.
                // If not, then we need to return 409.
                // If it is, then we need to return the console output.

                List<Container> containers = Bothive.client.listContainersCmd().exec();
                boolean containerFound = false;
                Container containerFoundObj = null;
                for (Container container : containers) {
                    if (container.getId().equals(bot.getString("container_id"))) {
                        containerFound = true;
                        containerFoundObj = container;
                        break;
                    }
                }

                if (!containerFound) {
                    return ResponseEntity.status(409).body(
                            new JSONObject()
                                    .put("message", "Bot is not running")
                                    .put("error", 409)
                                    .toString()
                    );
                }

                // If the container is found, then we need to stop it.
                Bothive.client.stopContainerCmd(containerFoundObj.getId()).exec();
                return ResponseEntity.ok(
                        new JSONObject()
                                .put("message", "Bot stopped")
                                .put("code", 200)
                                .toString()
                );
            }
            case RESTART -> {
                // First check if the bot is running
                if (!bot.contains("container_id") || bot.getString("container_id") == null || bot.getString("container_id").equals("")) {
                    return ResponseEntity.status(409).body(
                            new JSONObject()
                                    .put("message", "Bot is not running")
                                    .put("error", 409)
                                    .toString()
                    );
                }

                // If container ID is found, we need to first check if that is still a valid container.
                // If not, then we need to return 409.
                // If it is, then we need to return the console output.

                List<Container> containers = Bothive.client.listContainersCmd().exec();
                boolean containerFound = false;
                Container containerFoundObj = null;
                for (Container container : containers) {
                    if (container.getId().equals(bot.getString("container_id"))) {
                        containerFound = true;
                        containerFoundObj = container;
                        break;
                    }
                }

                if (!containerFound) {
                    return ResponseEntity.status(409).body(
                            new JSONObject()
                                    .put("message", "Bot is not running")
                                    .put("error", 409)
                                    .toString()
                    );
                }

                // If the container is found, then we need to restart it.
                Bothive.client.restartContainerCmd(containerFoundObj.getId()).exec();
                return ResponseEntity.ok(
                        new JSONObject()
                                .put("message", "Bot restarted")
                                .put("code", 200)
                                .toString()
                );
            }
            case START -> {
                System.out.println("Starting bot");
                // we need to create a new container for the bot.
                // First check if the bot is already running
                if (bot.contains("container_id") && bot.getString("container_id") != null && !bot.getString("container_id").equals("")) {
                    // Find the container and check if it is still running
                    List<Container> containers = Bothive.client.listContainersCmd().exec();
                    boolean containerFound = false;
                    Container containerFoundObj = null;
                    for (Container container : containers) {
                        if (container.getId().equals(bot.getString("container_id"))) {
                            containerFound = true;
                            containerFoundObj = container;
                            break;
                        }
                    }

                    if (containerFound) {
                        // Check if the container is still running
                        if (containerFoundObj.getState().equals("running")) {
                            return ResponseEntity.status(409).body(
                                    new JSONObject()
                                            .put("message", "Bot is already running")
                                            .put("error", 409)
                                            .toString()
                            );
                        }
                    }
                }

                System.out.println("Bot is not running, creating new container");

                    // If the container is not running, then we need to create a new container.
                    // We need to first delete the old container.
                    String containerId = bot.getString("container_id");
                    if (containerId != null && !containerId.equals("")) {
                        Bothive.client.removeContainerCmd(containerId).exec();
                    }

                    String botDirectory = "/var/bothive/bots/" + bot.getString("id");
                    // Check if the directory exists, if not, then we need to create it.
                    File botDirectoryFile = new File(botDirectory);
                    if (!botDirectoryFile.exists()) {
                        botDirectoryFile.mkdirs();
                    }

                    // Now we need to create a new container.
                    // To start, let's make sure we have the working directory all set up.
                    // We need to check if the bot has the "setup_file" value set to true.

                    if (!bot.contains("setup_file") || !Boolean.TRUE.equals(bot.getBoolean("setup_file"))) {
                        return ResponseEntity.status(409).body(
                                new JSONObject()
                                        .put("message", "Bot does not have a valid file")
                                        .put("error", 409)
                                        .toString()
                        );
                    }

                    // Now we're ready to create the container.
                    if (!bot.contains("start_command") || bot.getString("start_command") == null || bot.getString("start_command").equals("")) {
                        return ResponseEntity.status(409).body(
                                new JSONObject()
                                        .put("message", "Bot does not have a valid start command")
                                        .put("error", 409)
                                        .toString()
                        );
                    }

                    if (!bot.contains("port") || bot.get("port", Integer.class) == null || bot.get("port", Integer.class) == 0) {
                        return ResponseEntity.status(409).body(
                                new JSONObject()
                                        .put("message", "Bot does not have a valid port")
                                        .put("error", 409)
                                        .toString()
                        );
                    }

                System.out.println("Gone trough all checks");

                    int port = bot.get("port", Integer.class);
                    String startCommand = bot.getString("start_command");
                    String name = bot.getString("name");

                    // We can get going with creating the container.
                System.out.println("Creating container");
                    String newContainerId = createContainer(botDirectory, startCommand, port, name, bot.getString("id"));

                    // Update bot's container ID
                System.out.println("Updating bot's container ID");
                    Map<String, Object> data = bot.getData();
                    if (data == null) {
                        data = new HashMap<>();
                    }
                    data.put("container_id", newContainerId);

                    DocumentReference docRef = db.collection("bots").document(bot.getId());
                    ApiFuture<WriteResult> newFuture = docRef.update(data);
                System.out.println("Updated bot's container ID");

                    return ResponseEntity.ok(
                            new JSONObject()
                                    .put("message", "Bot started")
                                    .put("code", 200)
                                    .toString()
                    );
                }
        }

        return ResponseEntity.status(500).body(
                new JSONObject()
                        .put("message", "Something went wrong")
                        .put("error", 500)
                        .toString()
        );
    }

    public String createContainer(String botDirectory, String startCommand, int port, String name, String botId) throws InterruptedException {
        // Pull the Docker image if needed
        System.out.println("Pulling image");
        Bothive.client.pullImageCmd("openjdk")
                .withTag("17")
                .exec(new PullImageResultCallback())
                .awaitCompletion();
        System.out.println("Pulled image");

        String[] startCommandArray = startCommand.split(" ");

// Create a container
        CreateContainerCmd createContainerCmd = Bothive.client.createContainerCmd("openjdk:17")
                .withName(name)
                .withCmd(Arrays.stream(startCommandArray).toList())
                .withWorkingDir(botDirectory)
                .withExposedPorts(ExposedPort.tcp(port))
                .withPortBindings(PortBinding.parse(port + ":" + port))
                .withPublishAllPorts(true)
                .withBinds(Bind.parse("/var/bothive/bots/" + botId + ":/var/bothive/bots/" + botId));


        CreateContainerResponse containerResponse = createContainerCmd.exec();
        System.out.println("Created container");
        String containerId = containerResponse.getId();

        // Start the container
        Bothive.client.startContainerCmd(containerId).exec();
        return containerId;
    }

    public enum Status {
        START(0),
        STOP(1),
        RESTART(2),
        KILL(3);

        private int value;

        Status(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Status fromValue(int value) {
            for (Status status : Status.values()) {
                if (status.getValue() == value) {
                    return status;
                }
            }
            return null;
        }
    }

    @GetMapping("/console")
    public ResponseEntity<String> getConsole(@RequestParam("bot") String botId, @RequestHeader("Authorization") String accessToken, HttpServletResponse res) {
        try {

            String idToken = accessToken.replace("Bearer ", "");
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String userId = decodedToken.getUid();
            Firestore db = FirestoreClient.getFirestore();
            ApiFuture<QuerySnapshot> future = db.collection("bots").whereEqualTo("userId", userId).get();

            boolean found = false;
            DocumentSnapshot bot = null;
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (DocumentSnapshot document : documents) {
                if (document.getId().equals(botId)) {
                    found = true;
                    bot = document;
                    break;
                }
            }

            if (!found) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new JSONObject()
                                .put("message", "Bot not found")
                                .put("error", HttpStatus.NOT_FOUND.value())
                                .toString()
                );
            }

            if (!bot.contains("container_id") || bot.getString("container_id") == null || bot.getString("container_id").equals("")) {
                return ResponseEntity.status(409).body(
                        new JSONObject()
                                .put("message", "Bot is not running")
                                .put("error", 409)
                                .toString()
                );
            }

            // If container ID is found, we need to first check if that is still a valid container.
            // If not, then we need to return 409.
            // If it is, then we need to return the console output.

            List<Container> containers = Bothive.client.listContainersCmd().exec();
            boolean containerFound = false;
            Container containerFoundObj = null;
            for (Container container : containers) {
                if (container.getId().equals(bot.getString("container_id"))) {
                    containerFound = true;
                    containerFoundObj = container;
                    break;
                }
            }

            if (!containerFound) {
                return ResponseEntity.status(409).body(
                        new JSONObject()
                                .put("message", "Bot is not running")
                                .put("error", 409)
                                .toString()
                );
            }

            LogContainerCmd logContainerCmd = Bothive.client.logContainerCmd(containerFoundObj.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTimestamps(true)
                    .withTailAll();

            JSONArray logs = new JSONArray();
            logContainerCmd.exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame item) {
                    logs.put(
                            new JSONObject()
                                    .put("content", new String(item.getPayload()))
                    );
                }
            }).awaitCompletion();

            return ResponseEntity.status(200).body(logs.toString());
        } catch (FirebaseAuthException e) {
            // The token is invalid or expired
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new JSONObject()
                            .put("message", e.getMessage())
                            .put("error", HttpStatus.UNAUTHORIZED.value())
                            .toString()
            );
        } catch (Exception e) {
            // Handle any other exceptions
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new JSONObject()
                            .put("message", "Internal Server Error")
                            .put("error", HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .toString()
            );
        }
    }

}
