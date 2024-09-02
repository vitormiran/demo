package com.camunda_challenge.demo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@SpringBootApplication
@EnableAutoConfiguration(exclude = { MongoAutoConfiguration.class })
public class MicroserviceApp {

	public static void main(String[] args) {
		SpringApplication.run(MicroserviceApp.class, args);
	}

	@JobWorker(type = "run-service-task", fetchVariables = { "selected_type" })
	public Map<String, Object> orchestrateSomething(final ActivatedJob job) throws Error {

		String connectionString = "mongodb+srv://vitormiran:IsmrBrBhtArexUlS@cluster0.ebedtmb.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";

		try (MongoClient mongoClient = MongoClients.create(connectionString)) {
			MongoDatabase database = mongoClient.getDatabase("camundachallenge");
			MongoCollection<Document> collection = database.getCollection("pictures");
			System.out.println("count of documents " + collection.countDocuments());

			// Probably add some process variables
			HashMap<String, Object> variables = new HashMap<>();

			System.out.println("The animal selected was :" + job.getVariable("selected_type"));
			String api_url = "";
			if (job.getVariable("selected_type").equals("cat")) {
				api_url = "https://cataas.com/cat";
			} else if (job.getVariable("selected_type").equals("dog")) {
				api_url = "https://place.dog/300/200";
			} else if (job.getVariable("selected_type").equals("bear")) {
				api_url = "https://placebear.com/200/300";
			} else {
				throw new Error("Wrong animal provided");
			}

			OkHttpClient client = new OkHttpClient();
			Request request = new Request.Builder()
					.url(api_url)
					.build();

			try {
				byte[] image = getImageFromApi(api_url);
				ObjectId oid = new ObjectId();
				Document newPicture = new Document("_id", oid);
				newPicture.append("picture", image);
				newPicture.append("creation_date", new Date());
				newPicture.append("type", job.getVariable("selected_type"));
				newPicture.append("processInstanceKey", job.getProcessInstanceKey());
				collection.insertOne(newPicture);
				variables.put("oid_created", oid.toString());
				mongoClient.close();
			} catch (Exception e) {
				throw new Error("Error calling API");
			}

			return variables;
		}

	}

	public byte[] getImageFromApi(String apiUrl) throws Exception {
		URL url = new URL(apiUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Accept", "image/jpeg");

		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			try (InputStream in = connection.getInputStream();
					ByteArrayOutputStream out = new ByteArrayOutputStream()) {

				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = in.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}

				return out.toByteArray(); // Return the image as a byte array
			}
		} else {
			throw new RuntimeException("Failed to get image: HTTP error code : " + connection.getResponseCode());
		}
	}

}
