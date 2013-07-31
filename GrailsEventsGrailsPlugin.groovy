import grails.async.Promises
import org.grails.plugins.events.reactor.api.EventsApi
import org.grails.plugins.events.reactor.promise.ReactorPromiseFactory
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

class GrailsEventsGrailsPlugin {
	def version = "1.0.0.M1"
	def grailsVersion = "2.2 > *"
	def pluginExcludes = [
			"grails-app/views",
			"web-app",
			"lib",
			"scripts",
	]

	def packaging = "binary"

	def observe = [ "services" ]

	def title = "Grails Events Plugin" // Headline display name of the plugin
	def author = "Stephane Maldini"
	def authorEmail = "smaldini@gopivotal.com"
	def description = '''\
Grails Events based on Reactor API
'''

	def documentation = "http://grails.org/plugin/grails-events"
	def license = "APACHE"
	def organization = [name: "Pivotal", url: "http://www.gopivotal.com/"]
	def issueManagement = [system: "GITHUB", url: "https://github.com/reactor/grails-events/issues"]
	def scm = [url: "https://github.com/reactor/grails-events"]

	def doWithSpring = {
		Promises.promiseFactory = new ReactorPromiseFactory()

		instanceEventsApi(EventsApi)
	}

	def doWithDynamicMethods = { ctx ->
	}

	def doWithApplicationContext = { ctx ->
	}

	def onChange = { event ->
	}

	def onConfigChange = { event ->
	}

	def onShutdown = { event ->
	}
}
