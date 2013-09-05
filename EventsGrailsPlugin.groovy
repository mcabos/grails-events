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
import org.grails.plugins.events.reactor.api.EventsApi
import org.grails.plugins.events.reactor.configuration.ConsumerBeanPostProcessor
import org.grails.plugins.events.reactor.configuration.EventsArtefactHandler
import org.grails.plugins.events.reactor.configuration.ReactorConfigPostProcessor
import org.grails.plugins.events.reactor.gorm.GormReactorBridge
import org.springframework.context.ApplicationContext

class EventsGrailsPlugin {
	def version = "1.0.0.BUILD-SNAPSHOT"
	def grailsVersion = "2.2 > *"

	def pluginExcludes = [
			"grails-app/views",
			"grails-app/controllers",
			"grails-app/services",
			"grails-app/i18n",
			"grails-app/domain",
			"grails-app/taglib",
			"grails-app/utils",
			"grails-app/*/test/*",
			"grails-app/conf/TestEvents.groovy",
			"web-app",
			"lib",
			"scripts",
	]

	def packaging = "binary"

	def observe = ["services"]
	def after = ["services"]

	def watchedResources = [
			"file:./grails-app/conf/*Events.groovy",
			"file:./plugins/*/grails-app/conf/*Events.groovy"
	]

	def title = "Grails Events Plugin" // Headline display name of the plugin
	//def author = "Stephane Maldini"
	//def authorEmail = "smaldini@gopivotal.com"
	def developers = [ [name: "Stephane Maldini", email: "smaldini@gopivotal.com"] ]

	def description = '''\
Grails Events based on Reactor API
'''

	def documentation = "http://grails.org/plugin/grails-events"
	def license = "APACHE"
	def organization = [name: "Pivotal", url: "http://www.gopivotal.com/"]
	def issueManagement = [system: "GITHUB", url: "https://github.com/reactor/grails-events/issues"]
	def scm = [url: "https://github.com/reactor/grails-events"]


	def artefacts = [EventsArtefactHandler]

	def doWithSpring = {
		reactorBeanPostProcessor(ConsumerBeanPostProcessor)
		reactorConfigPostProcessor(ReactorConfigPostProcessor) {
			fixGroovyExtensions = application.config.grails.events.fixGroovyExtensions ?: true
		}

		instanceEventsApi(EventsApi)

		if (!application.config.grails.events.gorm.disable) {
			reactorGormBridge(GormReactorBridge)
		}

	}

	def doWithDynamicMethods = { ctx ->

	}

	def doWithApplicationContext = { ApplicationContext ctx ->
	}

	def onChange = { event ->
		if (event.source instanceof Class) {
			def ctx = event.application.mainContext
			if (application.isServiceClass(event.source)) {
				synchronized (ctx) {
					ctx.reactorConfigPostProcessor.scanServices(ctx, event.source)
				}
			} else if (application.isArtefactOfType(EventsArtefactHandler.TYPE, event.source)) {
				synchronized (ctx) {
					application.addArtefact(EventsArtefactHandler.TYPE, event.source)
					ctx.reactorConfigPostProcessor.initContext(ctx)
					if (!application.config.grails.events.gorm.disable) {
						ctx.reactorGormBridge.init()
					}
				}
			}

		}
	}

	def onConfigChange = { event ->
	}

	def onShutdown = { event ->
	}
}
