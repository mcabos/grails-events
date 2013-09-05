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
package test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import static grails.async.Promises.*

/**
 * @author Stephane Maldini
 */
class TestController {

	def grailsEvents

	def reactorConfigPostProcessor

	def onTest() {
		grailsEvents.on('test') {
			reply it
		}
		render ' on test '
	}

	def test() {
		grailsEvents.event('test', 1) {
			log.info 'eventCallback: ' + it
		}

		grailsEvents.event(for:'grailsReactor', key:'test', data:1) {
			log.info 'eventCallback-name: ' + it
		}

		grailsEvents.withStream {
			event(key:'test', data:1) {
				log.info 'eventCallback2: ' + it
			}
		} consume {
			log.info 'streamCallback:  ' + it
		} when(Exception) {
			log.info 'testException:'+it
		}

		def latch = new CountDownLatch(1)
		grailsEvents.event(key:'/someUri', data:1) {
			log.info 'uriCallback: ' + it
			latch.countDown()
		}

		latch.await(5, TimeUnit.SECONDS)

		new Book(title: 'lold').save()

		log.info 'count:' + grailsEvents.countConsumers('test')
		log.info 'remove test consumers:' + grailsEvents.removeConsumers('test')
		log.info 'remove /test consumers:' + grailsEvents.removeConsumers('/someUri')
		log.info 'recount:' + grailsEvents.countConsumers('test')
		reactorConfigPostProcessor.scanServices(applicationContext, TestService)
		log.info 'final count:' + grailsEvents.countConsumers('test')

		//tasks test: Book.async.list()
		//task{
			render Book.list().title
		//}
	}

}
