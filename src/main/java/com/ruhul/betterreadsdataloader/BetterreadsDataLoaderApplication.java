package com.ruhul.betterreadsdataloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.ruhul.betterreadsdataloader.author.Author;
import com.ruhul.betterreadsdataloader.author.AuthorRepository;
import com.ruhul.betterreadsdataloader.book.Book;
import com.ruhul.betterreadsdataloader.book.BookRepository;
import com.ruhul.betterreadsdataloader.connect.DataStaxAstraProperties;

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterreadsDataLoaderApplication {

	@Autowired
	AuthorRepository authorRepository;

	@Autowired
	BookRepository bookRepository;

	@Value("${datadump.location.author}")
	private String authorDumpLocation;

	@Value("${datadump.location.works}")
	private String worksDumpLocation;

	public static void main(String[] args) {
		SpringApplication.run(BetterreadsDataLoaderApplication.class, args);
	}

	private void initAutores() {
		Path path = Paths.get(authorDumpLocation);
		try (Stream<String> lines = Files.lines(path)) {
			lines.limit(150).forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				JSONObject jsonObject;
				try {
					jsonObject = new JSONObject(jsonString);

					// Create Author object
					Author author = new Author();
					author.setId(jsonObject.optString("key").replace("/authors/", ""));
					author.setName(jsonObject.optString("name"));
					author.setPersonalName(jsonObject.optString("personal_name"));

					// Persist using Repository
					System.out.println("Saving author " + author.getName() + " ...");
					authorRepository.save(author);
				} catch (JSONException e) {
					e.printStackTrace();
				}

			});

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void initWorks() {
		Path path = Paths.get(worksDumpLocation);
		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
		try (Stream<String> lines = Files.lines(path)) {
			lines.limit(150).forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				JSONObject jsonObject;
				try {
					jsonObject = new JSONObject(jsonString);

					// Create Book object
					Book book = new Book();
					book.setId(jsonObject.optString("key").replace("/works/", ""));
					book.setName(jsonObject.optString("title"));

					JSONObject descriptionObject = jsonObject.optJSONObject("description");
					if (descriptionObject != null)
						book.setDescription(descriptionObject.optString("value"));

					JSONObject publishedDateObj = jsonObject.optJSONObject("created");
					if (publishedDateObj != null) {
						String dateStr = publishedDateObj.getString("value");
						book.setPublishedDate(LocalDate.parse(dateStr, dateFormat));
					}

					JSONArray coversJSONArr = jsonObject.optJSONArray("covers");
					if (coversJSONArr != null) {
						List<String> coverIds = new ArrayList<>();
						for (int i = 0; i < coversJSONArr.length(); i++) {
							coverIds.add(coversJSONArr.getString(i));
						}
						book.setCoverIds(coverIds);
					}

					JSONArray authorJSONArr = jsonObject.optJSONArray("authors");
					if (authorJSONArr.isNull(0)) {
						List<String> authorIds = new ArrayList<>();
						for (int i = 0; i < coversJSONArr.length(); i++) {
							String authorId = authorJSONArr.getJSONObject(i).getJSONObject("author").getString("key")
									.replace("/authors/", "");
							authorIds.add(authorId);
						}
						book.setAuthorIds(authorIds);
						List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id))
								.map(optionalAuthor -> {
									if (!optionalAuthor.isPresent())
										return "Unknown Author";
									return optionalAuthor.get().getName();
								}).collect(Collectors.toList());
						book.setAuthorNames(authorNames);
					}

					// Persist using Repository
					System.out.println("Saving book " + book.getName() + " ...");
					bookRepository.save(book);
				} catch (JSONException e) {
					e.printStackTrace();
				}

			});

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@PostConstruct
	public void start() {
		System.out.println("Application started");
		//initAutores();
		//initWorks();
		System.out.println("Data load is completed");
	}

	/**
	 * This is necessary to have the Spring Boot app use the Astra secure bundle to
	 * connect to the database
	 */
	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}
}
