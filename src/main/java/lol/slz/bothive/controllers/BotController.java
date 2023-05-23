package lol.slz.bothive.controllers;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@CrossOrigin(origins = "*")
@RestController
public class BotController {

    /**
     * Retrieves the current user's bots.
     */
    @GetMapping("/bots")
    public ResponseEntity<String> getBots(@RequestHeader("Authorization") String accessToken, HttpServletResponse res) {
        try {

            String idToken = accessToken.replace("Bearer ", "");
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String userId = decodedToken.getUid();
            Firestore db = FirestoreClient.getFirestore();
            ApiFuture<QuerySnapshot> future = db.collection("bots").whereEqualTo("userId", userId).get();
            JSONArray bots = new JSONArray();

            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (DocumentSnapshot document : documents) {
                bots.put(document.getData());
            }

            return ResponseEntity.status(200).body(bots.toString());
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

    @PostMapping("/bot_file")
    public ResponseEntity<String> uploadBotFile(@RequestParam("file") MultipartFile file, @RequestHeader("Authorization") String accessToken, @RequestParam("bot") String botId) throws ExecutionException, InterruptedException {
        String idToken = accessToken.replace("Bearer ", "");
        FirebaseToken decodedToken = null;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new JSONObject()
                            .put("message", e.getMessage())
                            .put("error", HttpStatus.UNAUTHORIZED.value())
                            .toString()
            );
        }
        String userId = decodedToken.getUid();
        Firestore db = FirestoreClient.getFirestore();
        ApiFuture<QuerySnapshot> future = db.collection("bots").whereEqualTo("userId", userId).get();


        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        boolean found = false;
        QueryDocumentSnapshot bot = null;
        for (QueryDocumentSnapshot document : documents) {
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

        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject()
                            .put("message", "Missing file")
                            .put("error", HttpStatus.BAD_REQUEST.value())
                            .toString()
            );
        }

        // Put this file in var/bothive/bots/{botId}/bot.jar
        File dir = new File("var/bothive/bots/" + botId);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File dest = new File("var/bothive/bots/" + botId + "/bot.jar");
        try {
            file.transferTo(dest);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new JSONObject()
                            .put("message", "Internal Server Error")
                            .put("error", HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .toString()
            );
        }

        return ResponseEntity.status(HttpStatus.OK).body(
                new JSONObject()
                        .put("message", "File uploaded successfully")
                        .toString()
        );
    }

    @PostMapping("/bots")
    public ResponseEntity<String> createBot(@RequestHeader("Authorization") String accessToken, @RequestBody String body) {
        JSONObject json;
        try {
            json = new JSONObject(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject()
                            .put("message", "Invalid JSON")
                            .put("error", HttpStatus.BAD_REQUEST.value())
                            .toString()
            );
        }

        if (!json.has("name") || json.isNull("name") || json.getString("name").isEmpty() || json.getString("name").length() > 20 || json.getString("name").length() < 3) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject()
                            .put("message", "Missing parameters. Required: name")
                            .put("error", HttpStatus.BAD_REQUEST.value())
                            .toString()
            );
        }

        // Check if it has start_command
        if (!json.has("start_command") || json.isNull("start_command") || json.getString("start_command").isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject()
                            .put("message", "Missing parameters. Required: start_command")
                            .put("error", HttpStatus.BAD_REQUEST.value())
                            .toString()
            );
        }

        String id = UUID.randomUUID().toString();
        String createdAt = DateTime.now().toString();

        String idToken = accessToken.replace("Bearer ", "");
        FirebaseToken decodedToken = null;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new JSONObject()
                            .put("message", e.getMessage())
                            .put("error", HttpStatus.UNAUTHORIZED.value())
                            .toString()
            );
        }

        String userId = decodedToken.getUid();
        Firestore db = FirestoreClient.getFirestore();

        // Insert into firestore
        DocumentReference docRef = db.collection("bots").document(id);

        HashMap<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", json.getString("name"));
        data.put("userId", userId);
        data.put("createdAt", createdAt);
        data.put("start_command", json.getString("start_command"));
        ApiFuture<WriteResult> future = docRef.set(data);

        try {
            future.get();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new JSONObject()
                            .put("message", "Internal Server Error")
                            .put("error", HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .toString()
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new JSONObject()
                        .put("id", id)
                        .put("name", json.getString("name"))
                        .put("userId", userId)
                        .put("createdAt", createdAt)
                        .toString()
        );
    }


}
