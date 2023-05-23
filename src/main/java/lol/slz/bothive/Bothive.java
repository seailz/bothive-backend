package lol.slz.bothive;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.auth.oauth2.GoogleCredentials;

@SpringBootApplication
public class Bothive {

    public static List<Container> containers = new ArrayList<>();
    public static DockerClient client;

    public static void main(String[] args) {
        // TODO: Implement json config
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        client = dockerClient;

        // Get a list of running containers
        containers = dockerClient.listContainersCmd().exec();


        InputStream serviceAccount = null;
        try {
            serviceAccount = new FileInputStream("firebaseCredentials.json");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        GoogleCredentials credentials = null;
        try {
            credentials = GoogleCredentials.fromStream(serviceAccount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setProjectId("seailz-bh")
                .setCredentials(credentials)
                .build();
        FirebaseApp.initializeApp(options);
        SpringApplication.run(Bothive.class, args);
    }

}
