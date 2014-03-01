/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.repository.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * Delegate for configuration integration to reuse the general way of detecting repositories. Customization is done by
 * providing a configuration format specific {@link RepositoryConfigurationSource} (currently either XML or annotations
 * are supported). The actual registration can then be triggered for different {@link RepositoryConfigurationExtension}
 * s.
 * 
 * @author Oliver Gierke
 */
public class RepositoryConfigurationDelegate {

	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryConfigurationDelegate.class);

	private final RepositoryConfigurationSource configurationSource;
	private final ResourceLoader resourceLoader;
	private final BeanNameGenerator generator;
	private final boolean isXml;

	/**
	 * Creates a new {@link RepositoryConfigurationDelegate} for the given {@link RepositoryConfigurationSource} and
	 * {@link ResourceLoader}.
	 * 
	 * @param configurationSource must not be {@literal null}.
	 * @param resourceLoader must not be {@literal null}.
	 */
	public RepositoryConfigurationDelegate(RepositoryConfigurationSource configurationSource,
			ResourceLoader resourceLoader) {

		this.isXml = configurationSource instanceof XmlRepositoryConfigurationSource;
		boolean isAnnotation = configurationSource instanceof AnnotationRepositoryConfigurationSource;

		Assert.isTrue(isXml || isAnnotation,
				"Configuration source must either be an Xml- or an AnnotationBasedConfigurationSource!");
		Assert.notNull(resourceLoader);

		RepositoryBeanNameGenerator generator = new RepositoryBeanNameGenerator();
		generator.setBeanClassLoader(resourceLoader.getClassLoader());

		this.generator = generator;
		this.configurationSource = configurationSource;
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Registers the found repositories in the given {@link BeanDefinitionRegistry}.
	 * 
	 * @param registry
	 * @param extension
	 * @return {@link BeanComponentDefinition}s for all repository bean definitions found.
	 */
	public List<BeanComponentDefinition> registerRepositoriesIn(BeanDefinitionRegistry registry,
			RepositoryConfigurationExtension extension) {

		extension.registerBeansForRoot(registry, configurationSource);

		RepositoryBeanDefinitionBuilder builder = new RepositoryBeanDefinitionBuilder(registry, extension, resourceLoader);
		List<BeanComponentDefinition> definitions = new ArrayList<BeanComponentDefinition>();

		for (RepositoryConfiguration<? extends RepositoryConfigurationSource> configuration : extension
				.getRepositoryConfigurations(configurationSource, resourceLoader)) {

			BeanDefinitionBuilder definitionBuilder = builder.build(configuration);

			extension.postProcess(definitionBuilder, configurationSource);

			if (isXml) {
				extension.postProcess(definitionBuilder, (XmlRepositoryConfigurationSource) configurationSource);
			} else {
				extension.postProcess(definitionBuilder, (AnnotationRepositoryConfigurationSource) configurationSource);
			}

			AbstractBeanDefinition beanDefinition = definitionBuilder.getBeanDefinition();
			String beanName = generator.generateBeanName(beanDefinition, registry);

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Registering repository: " + beanName + " - Interface: " + configuration.getRepositoryInterface()
						+ " - Factory: " + extension.getRepositoryFactoryClassName());
			}

			registry.registerBeanDefinition(beanName, beanDefinition);
			definitions.add(new BeanComponentDefinition(beanDefinition, beanName));
		}

		return definitions;
	}
}
