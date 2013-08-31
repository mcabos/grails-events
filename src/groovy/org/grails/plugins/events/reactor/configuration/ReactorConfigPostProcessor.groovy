/*
 * Copyright (c) 2011-2013 GoPivotal, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.events.reactor.configuration

import grails.async.Promises
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.ServiceArtefactHandler
import org.codehaus.groovy.grails.plugins.metadata.GrailsPlugin
import org.codehaus.groovy.runtime.m12n.ExtensionModuleScanner
import org.grails.plugins.events.reactor.api.EventsApi
import org.grails.plugins.events.reactor.promise.ReactorPromiseFactory
import org.springframework.beans.factory.BeanFactory
import reactor.spring.beans.factory.config.ConsumerBeanPostProcessor as C
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.Ordered
import reactor.core.Reactor
import reactor.groovy.config.GroovyEnvironment

import java.lang.reflect.Method
import org.codehaus.groovy.reflection.CachedClass
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl

/**
 * @author Stephane Maldini
 */
@CompileStatic
class ReactorConfigPostProcessor implements Ordered, BeanFactoryPostProcessor {

	private static final Logger log = Logger.getLogger(ReactorConfigPostProcessor)

	boolean fixGroovyExtensions = false
	boolean enableReactorPromise = true

	@Override
	void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
		if (fixGroovyExtensions){
			fixGroovyExtensions()
			fixGroovyExtensions = false
		}

		initContext configurableListableBeanFactory
	}

	void initContext(BeanFactory bf) {
		bf.with {
			def grailsApplication = getBean(GrailsApplication)
			def eventsApi = getBean(EventsApi)

			//fix autowiring
			def consumerBeanProcessor = getBean(ConsumerBeanPostProcessor)
			consumerBeanProcessor.eventsApi = eventsApi

			reloadConfiguration grailsApplication, eventsApi

			def artefacts = grailsApplication.getArtefacts(ServiceArtefactHandler.TYPE)
			def classes = []
			for (final GrailsClass artefact : artefacts) {
				classes << artefact.clazz
			}

			scanServices(bf, classes as Class[])

		}
	}

	void scanServices(BeanFactory bf, Class... classes) {
		Set<Method> methods
		def consumerBeanPostProcessor = bf.getBean(ConsumerBeanPostProcessor)
		for (clazz in classes) {
			methods = C.findHandlerMethods(clazz, ConsumerBeanPostProcessor.CONSUMER_METHOD_FILTER)
			if (methods)
				consumerBeanPostProcessor.initBean(bf.getBean(clazz), methods)
		}
	}

	GroovyEnvironment reloadConfiguration(GrailsApplication grailsApplication, EventsApi eventsApi) {
		Script dslInstance
		GroovyEnvironment current, previous
		Map<Integer, GroovyEnvironment> sortedEnvs = [:]
		String pluginName
		int i = 0

		for (artefact in grailsApplication.getArtefacts(EventsArtefactHandler.TYPE)) {
			if (log.debugEnabled) {
				log.debug "Loading events artefact [${artefact.clazz}] (class instance hash: ${System.identityHashCode(artefact.clazz)})"
			}

			dslInstance = artefact.clazz.newInstance() as Script
			dslInstance.binding["grailsApplication"] = grailsApplication
			dslInstance.binding["ctx"] = grailsApplication.mainContext
			dslInstance.binding["config"] = grailsApplication.config
			dslInstance.run()
			Closure dsl = dslInstance.binding['doWithReactor'] ? dslInstance.binding['doWithReactor'] as Closure : null
			if (dsl) {
				i++
				pluginName = dslInstance?.getClass()?.getAnnotation(GrailsPlugin)?.name()
				current = GroovyEnvironment.create dsl
				sortedEnvs[pluginName ? -i : i] = current
			} else {
				log.warn "Tried to load events data from artefact [${artefact.clazz}] but no 'events' value was found in the " +
						"script"
			}
		}

		for (env in sortedEnvs.sort()) {
			current = ((Map.Entry<Integer, GroovyEnvironment>) env).value
			if (previous)
				current.include(previous)
			previous = current
		}

		//at least one environment
		assert current
		eventsApi.groovyEnvironment = current
		eventsApi.appReactor = (Reactor) current[EventsApi.GRAILS_REACTOR]

		if (enableReactorPromise)
			Promises.promiseFactory = new ReactorPromiseFactory(current.environment())

		current
	}

	static private void fixGroovyExtensions() {
		Map<CachedClass, List<MetaMethod>> map = [:]

		ClassLoader classLoader = Thread.currentThread().contextClassLoader

		try {
			Enumeration<URL> resources = classLoader.getResources(ExtensionModuleScanner.MODULE_META_INF_FILE)
			URL url
			while (resources.hasMoreElements()) {
				url = resources.nextElement()
				if (url.path.contains('groovy-all')) {
					// already registered
					continue
				}
				Properties properties = new Properties()
				InputStream inStream
				try {
					inStream = url.openStream()
					properties.load(inStream)
					((MetaClassRegistryImpl) GroovySystem.metaClassRegistry).registerExtensionModuleFromProperties(
							properties,
							classLoader,
							map
					)
				}
				catch (IOException e) {
					throw new GroovyRuntimeException("Unable to load module META-INF descriptor", e)
				}
				finally {
					inStream?.close()
				}
			}
		}
		catch (IOException ignored) {
		}

		map.each { CachedClass cls, List<MetaMethod> methods ->
			cls.setNewMopMethods(methods)
		}

	}

	@Override
	int getOrder() {
		LOWEST_PRECEDENCE - 1
	}
}
