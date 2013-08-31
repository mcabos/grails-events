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

import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.ServiceArtefactHandler
import org.codehaus.groovy.grails.plugins.metadata.GrailsPlugin
import org.grails.plugins.events.reactor.api.EventsApi
import org.springframework.beans.factory.BeanFactory
import reactor.spring.beans.factory.config.ConsumerBeanPostProcessor as C
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.Ordered
import reactor.core.Reactor
import reactor.groovy.config.GroovyEnvironment

import java.lang.reflect.Method
/**
 * @author Stephane Maldini
 */
@CompileStatic
class ReactorConfigPostProcessor implements Ordered, BeanFactoryPostProcessor {

	private static final Logger log = Logger.getLogger(ReactorConfigPostProcessor)

	@Override
	void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
		initContext configurableListableBeanFactory
	}

	void initContext(BeanFactory bf){
		bf.with {
			def grailsApplication = getBean(GrailsApplication)
			def eventsApi = getBean(EventsApi)

			//fix autowiring
			def consumerBeanProcessor = getBean(ConsumerBeanPostProcessor)
			consumerBeanProcessor.eventsApi = eventsApi

			reloadConfiguration grailsApplication, eventsApi

			scanServices(bf, grailsApplication.getArtefacts(ServiceArtefactHandler.TYPE) as GrailsClass[])

		}
	}

	void scanServices(BeanFactory bf, GrailsClass... classes){
		Set<Method> methods
		def consumerBeanPostProcessor = bf.getBean(ConsumerBeanPostProcessor)
		for(artefact in classes){
			methods = C.findHandlerMethods(artefact.clazz, ConsumerBeanPostProcessor.CONSUMER_METHOD_FILTER)
			if(methods)
				consumerBeanPostProcessor.initBean(bf.getBean(artefact.clazz), methods)
		}
	}

	static GroovyEnvironment reloadConfiguration(GrailsApplication grailsApplication, EventsApi eventsApi) {
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
		current
	}

	@Override
	int getOrder() {
		LOWEST_PRECEDENCE - 1
	}
}
