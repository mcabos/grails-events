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
package grails.events.transformer

import grails.util.BuildSettings
import grails.util.GrailsWebUtil
import grails.web.Action

import org.codehaus.groovy.grails.commons.spring.GrailsWebApplicationContext
import org.codehaus.groovy.grails.compiler.injection.ClassInjector
import org.codehaus.groovy.grails.compiler.injection.GrailsAwareClassLoader
import org.codehaus.groovy.grails.compiler.reactor.ReactorTransformer
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification
/**
 * @author Stephane Maldini
 */
class ReactorTransformerSpec extends Specification{

	def gcl

	void setup() {
		gcl = new GrailsAwareClassLoader()
		def transformer = new ReactorTransformer(){
			@Override
			boolean shouldInject(URL url) { true }
		}

		gcl.classInjectors = [transformer] as ClassInjector[]
		def webRequest = GrailsWebUtil.bindMockWebRequest()
		def appCtx = new GrailsWebApplicationContext()
		def servletContext = webRequest.servletContext
		servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx)
	}

	void "Test that a closure action has changed to method"() {

		when:
			def cls = gcl.parseClass('''
            class SomeService {

                def action() {
                  event()
                }

                }
            ''')
			def service = cls.newInstance()

		then:
			service
			service.instanceEventsApi
			service.action()
	}


}