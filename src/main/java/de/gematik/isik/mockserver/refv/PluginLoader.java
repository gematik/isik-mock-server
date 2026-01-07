package de.gematik.isik.mockserver.refv;

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

import de.gematik.refv.Plugin;
import de.gematik.refv.SupportedValidationModule;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

@Component
@Slf4j
public class PluginLoader {

	@Getter
	private final Map<String, Plugin> plugins = new HashMap<>();

	private final String pluginPath;

	@Getter
	private final boolean enabled;

	public PluginLoader(
			@Value("${plugins.directory}") String pluginPath, @Value("${plugins.enabled:true}") boolean enabled) {
		this.pluginPath = pluginPath;
		this.enabled = enabled;
	}

	private File pluginFolder;

	@PostConstruct
	public void init() {
		if (enabled) {
			this.pluginFolder = new File(pluginPath);
			loadPlugins();
		}
	}

	private void loadPlugins() {
		plugins.clear();

		var zipFiles = getResourcesFromFolder(pluginPath);
		if (zipFiles.isEmpty()) {
			log.info("No plugins found in: {}", pluginFolder.getPath());
			return;
		}

		for (var zipFile : zipFiles) {
			var configFile = zipFile.stream()
					.filter(e -> e.getName().endsWith("config.yaml"))
					.findFirst();
			if (configFile.isEmpty()) {
				throw new IllegalArgumentException("No config file found for plugin " + zipFile.getName());
			}

			Plugin plugin = Plugin.createFromZipFile(zipFile);
			if (plugins.containsKey(plugin.getId())) {
				log.warn(
						"Duplicate plugin id found: '{}'. Change the id of the plugin in the plugin"
								+ " configuration file and try again",
						plugin.getId());
			} else {
				plugins.put(plugin.getId(), plugin);
			}
		}

		log.info("Loaded {} plugins successfully.", plugins.size());
	}

	@SneakyThrows
	private List<ZipFile> getResourcesFromFolder(final String folder) {

		final List<ZipFile> resources = new ArrayList<>();

		if (!tryReadResourcesFromJarIfInJarEnvironment(folder, resources))
			readResourcesFromFolderIfNonJarEnvironment(folder, resources);

		return resources;
	}

	@SneakyThrows
	private void readResourcesFromFolderIfNonJarEnvironment(String folder, List<ZipFile> resources) {
		var paths = getAllFilesFromResourceSubfolder(folder);
		for (File file : paths) {
			Path path = file.toPath();
			ZipFile resource;
			try (final var bomStream = new BOMInputStream(Files.newInputStream(path))) {
				resource = convertToZipFile(bomStream, path.toString());
			}
			resources.add(resource);
		}
	}

	@SneakyThrows
	private ZipFile convertToZipFile(BOMInputStream bomStream, String string) {
		// Convert the BOMInputStream to ZipFile
		File tempFile = File.createTempFile("plugin-" + string + "-", ".zip");
		tempFile.deleteOnExit();
		try (OutputStream out = new FileOutputStream(tempFile)) {
			bomStream.transferTo(out);
		}
		return new ZipFile(tempFile);

		//		var tempFile = Files.createTempFile("plugin", "");
		//		return new ZipFile()
	}

	@SneakyThrows
	private List<File> getAllFilesFromResourceSubfolder(String folder) {

		ClassLoader classLoader = getClass().getClassLoader();

		URL resource = classLoader.getResource(folder);
		URI uri = resource != null ? resource.toURI() : null;
		if (uri == null) throw new IllegalStateException("Could not retrieve resource folder: " + folder);

		List<File> result;
		try (Stream<Path> stream = Files.walk(Paths.get(uri))) {
			result = stream.filter(Files::isRegularFile).map(Path::toFile).toList();
		}
		return result;
	}

	@SneakyThrows
	private boolean tryReadResourcesFromJarIfInJarEnvironment(@NonNull String folder, @NonNull List<ZipFile> output) {
		try {
			ClassLoader cl = this.getClass().getClassLoader();
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
			org.springframework.core.io.Resource[] jarResources =
					resolver.getResources("classpath*:/" + folder + "/*.*");

			for (Resource resource : jarResources) {
				ZipFile r;
				try (final var bomStream = new BOMInputStream(resource.getInputStream())) {
					String filename = resource.getFilename();
					if (filename == null) throw new IllegalStateException("Could not retrieve resource filename");

					r = convertToZipFile(bomStream, filename);
				}
				output.add(r);
			}

			return true;
		} catch (ProviderNotFoundException e) {
			log.debug("Could not read resources from JAR", e);
			return false;
		}
	}

	@SneakyThrows
	private static ZipFile getZipFile(String pluginPath, String fileName) {
		return new ZipFile(pluginPath + File.separator + fileName);
	}

	public Plugin getPlugin(String validationModuleId) {
		Plugin plugin = plugins.get(validationModuleId);
		if (plugin == null) {
			List<String> supportedValidationModules = Stream.concat(
							plugins.keySet().stream(), Arrays.stream(SupportedValidationModule.values()))
					.map(Object::toString)
					.toList();

			throw new IllegalArgumentException(String.format(
					"Validation module [%s] unsupported. Supported validation modules: %s",
					validationModuleId, supportedValidationModules));
		}
		return plugin;
	}
}
