package ca.uhn.fhir.jpa.starter.common.init;

/*-
 * #%L
 * isik-mock-server
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes
 * by gematik, find details in the "Readme" file.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import de.gematik.isik.mockserver.interceptor.FhirValidationHandler;
import de.gematik.refv.commons.exceptions.ValidationModuleInitializationException;
import de.gematik.refv.commons.validation.ValidationResult;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BOMInputStream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class ResourceLoader {
	private static final String CONFORMANCE_RESOURCES_FOLDER = "conformance";

	@Value("${example-fhir-resources.directory:example-resources}")
	private String exampleResourcesDirectory;

	@Autowired
	private DaoRegistry daoRegistry;

	@Autowired
	private FhirContext fhirContext;

	@Autowired(required = false)
	private FhirValidationHandler validationHandler;

	@Value("${example-fhir-resources.validation.enabled:false}")
	private boolean validationEnabled;

	private IParser jsonParser;
	private IParser xmlParser;

	@PostConstruct
	public void postConstruct() {
		jsonParser = fhirContext.newJsonParser();
		xmlParser = fhirContext.newXmlParser();
		if (validationEnabled && validationHandler == null) {
			log.warn("Validation enabled but no FhirValidationHandler bean found; skipping validation.");
		}
	}

	public String getExampleResourcesDirectory() {
		return exampleResourcesDirectory;
	}

	public void loadResources() {
		final List<IBaseResource> resourcesFromFolder = getResourcesFromFolder(CONFORMANCE_RESOURCES_FOLDER);
		for (final IBaseResource r : resourcesFromFolder) {
			log.info(
					"Loading/Updating resource: {} - {}",
					r.getClass(),
					r.getIdElement().getIdPart());
			upsertExampleInServer(r);
		}
	}

	public void loadResourcesAsTransaction() {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);

		List<IBaseResource> resourcesFromFolder = getResourcesFromFolder(exampleResourcesDirectory);

		List<Bundle.BundleEntryComponent> entries = resourcesFromFolder.parallelStream()
				.map(r -> {
					log.info(
							"Preparing resource for loading: {} - {}",
							r.getClass(),
							r.getIdElement().getIdPart());

					String jsonString = jsonParser.encodeResourceToString(r);

					boolean shouldValidate = validationEnabled && validationHandler != null;
					if (shouldValidate) {
						ValidationResult validationResult;
						try {
							validationResult = validationHandler.validateResource(r, jsonString);
						} catch (ValidationModuleInitializationException e) {
							log.error(
									"Validation module initialization failed for resource: {} - {}",
									r.getClass(),
									r.getIdElement().getIdPart(),
									e);
							return null;
						}

						if (!validationResult.isValid()) {
							String concatenatedMessages = validationResult.getValidationMessages().stream()
									.map(message -> "["
											+ message.getSeverity()
											+ "] "
											+ message.getMessage()
											+ " - ID: "
											+ message.getMessageId())
									.collect(Collectors.joining(", "));
							log.warn(
									"Resource with ID: {} is invalid. Skipping resource. Cause: {}",
									r.getIdElement().getIdPart(),
									concatenatedMessages);
							return null;
						}
					} else {
						log.info(
								"Validation disabled for resource: {} - {}",
								r.getClass(),
								r.getIdElement().getIdPart());
					}

					Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
					entry.setResource((org.hl7.fhir.r4.model.Resource) r)
							.getRequest()
							.setMethod(Bundle.HTTPVerb.PUT)
							.setUrl(((org.hl7.fhir.r4.model.Resource) r).getResourceType()
									+ "/"
									+ r.getIdElement().getIdPart());
					return entry;
				})
				.filter(Objects::nonNull)
				.toList();

		entries.forEach(bundle::addEntry);

		upsertExampleInServer(bundle);
	}

	@SneakyThrows
	private List<IBaseResource> getResourcesFromFolder(final String folder) {
		final List<IBaseResource> resources = new ArrayList<>();

		// 1) Classpath (works for IDE + packaged JAR/WAR)
		if (tryReadResourcesFromClasspath(folder, resources) && !resources.isEmpty()) {
			return resources;
		}

		// 2) Filesystem directory (external folder)
		log.info("Attempting to read resources from filesystem folder: {}", folder);
		readResourcesFromFilesystemFolderIfExists(folder, resources);

		return resources;
	}

	@SneakyThrows
	private boolean tryReadResourcesFromClasspath(@NonNull String folder, @NonNull List<IBaseResource> output) {
		try {
			ClassLoader cl = this.getClass().getClassLoader();
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
			Resource[] classpathResources = resolver.getResources("classpath*:/" + folder + "/*.*");

			for (Resource resource : classpathResources) {
				IBaseResource r;
				try (InputStream in = resource.getInputStream();
						BOMInputStream bomStream = new BOMInputStream(in)) {
					String filename = resource.getFilename();
					if (filename == null) {
						throw new IllegalStateException("Could not retrieve resource filename");
					}
					r = convertToFhirResource(bomStream, filename);
				}
				output.add(r);
			}

			log.info("Loaded {} resources from classpath folder `{}`", output.size(), folder);
			return true;
		} catch (Exception e) {
			log.info("Could not read resources from classpath folder `{}`", folder, e);
			return false;
		}
	}

	@SneakyThrows
	private void readResourcesFromFilesystemFolderIfExists(String folder, List<IBaseResource> resources) {
		Path dir = Paths.get(folder).toAbsolutePath().normalize();
		if (!Files.isDirectory(dir)) {
			log.debug("Filesystem folder `{}` does not exist or is not a directory; skipping.", dir);
			return;
		}

		try (Stream<Path> stream = Files.walk(dir)) {
			List<Path> files = stream.filter(Files::isRegularFile)
					.filter(p -> {
						String name = p.getFileName().toString().toLowerCase();
						return name.endsWith(".json") || name.endsWith(".xml");
					})
					.toList();

			for (Path path : files) {
				IBaseResource resource;
				try (BOMInputStream bomStream = new BOMInputStream(Files.newInputStream(path))) {
					resource =
							convertToFhirResource(bomStream, path.getFileName().toString());
				}
				resources.add(resource);
			}

			log.info("Loaded {} resources from filesystem folder `{}`", resources.size(), dir);
		}
	}

	@SneakyThrows
	private void readResourcesFromFolderIfNonJarEnvironment(String folder, List<IBaseResource> resources) {
		var paths = getAllFilesFromResourceSubfolder(folder);
		for (File file : paths) {
			Path path = file.toPath();
			IBaseResource resource;
			try (final var bomStream = new BOMInputStream(Files.newInputStream(path))) {
				resource = convertToFhirResource(bomStream, path.toString());
			}
			resources.add(resource);
		}

		log.info("Loaded {} resources from folder '{}'", resources.size(), folder);
	}

	@SneakyThrows
	private boolean tryReadResourcesFromJarIfInJarEnvironment(
			@NonNull String folder, @NonNull List<IBaseResource> output) {
		try {
			ClassLoader cl = this.getClass().getClassLoader();
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
			Resource[] jarResources = resolver.getResources("classpath*:/" + folder + "/*.*");

			for (Resource resource : jarResources) {
				IBaseResource r;
				try (final var bomStream = new BOMInputStream(resource.getInputStream())) {
					String filename = resource.getFilename();
					if (filename == null) throw new IllegalStateException("Could not retrieve resource filename");

					r = convertToFhirResource(bomStream, filename);
				}
				output.add(r);
			}
			log.info("Loaded {} resources from JAR folder '{}'", output.size(), folder);
			return true;
		} catch (Exception e) {
			log.info("Could not read resources from JAR", e);
			return false;
		}
	}

	private IBaseResource convertToFhirResource(BOMInputStream bomStream, String filename) {
		IBaseResource r;
		if (filename.endsWith("json")) {
			r = jsonParser.parseResource(bomStream);
		} else if (filename.endsWith("xml")) {
			r = xmlParser.parseResource(bomStream);
		} else throw new IllegalArgumentException("Unsupported file type: " + filename);

		return r;
	}

	@SneakyThrows
	private List<File> getAllFilesFromResourceSubfolder(String folder) {

		ClassLoader classLoader = getClass().getClassLoader();

		URL resource = classLoader.getResource(folder);
		if (resource == null) {
			log.debug("Resource folder '{}' not found on classpath; skipping load.", folder);
			return List.of();
		}

		URI uri = resource.toURI();
		try (Stream<Path> stream = Files.walk(Paths.get(uri))) {
			return stream.filter(Files::isRegularFile).map(Path::toFile).toList();
		}
	}

	private void upsertExampleInServer(final IBaseResource resource) {
		if (resource instanceof Bundle resourceBundle) {
			daoRegistry.getSystemDao().transaction(null, resourceBundle);
			return;
		}
		final IFhirResourceDao<IBaseResource> dao =
				daoRegistry.getResourceDao((Class<IBaseResource>) resource.getClass());
		dao.update(resource, (RequestDetails) null);
	}
}
