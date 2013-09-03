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

	def instanceEventsApi
	def reactorConfigPostProcessor

	def onTest() {
		instanceEventsApi.on('test') {
			reply it
		}
		render ' on test '
	}

	def test() {
		instanceEventsApi.event('test', 1) {
			log.info 'eventCallback: ' + it
		}

		instanceEventsApi.event(for:'grailsReactor', key:'test', data:1) {
			log.info 'eventCallback-name: ' + it
		}

		instanceEventsApi.withStream {
			event(key:'test', data:1) {
				log.info 'eventCallback2: ' + it
			}
		} consume {
			log.info 'streamCallback:  ' + it
		} when(Exception) {
			log.info 'testException:'+it
		}

		def latch = new CountDownLatch(1)
		instanceEventsApi.event(key:'/someUri', data:1) {
			log.info 'uriCallback: ' + it
			latch.countDown()
		}

		latch.await(5, TimeUnit.SECONDS)

		log.info 'count:' + instanceEventsApi.countConsumers('test')
		log.info 'remove test consumers:' + instanceEventsApi.removeConsumers('test')
		log.info 'remove /test consumers:' + instanceEventsApi.removeConsumers('/someUri')
		log.info 'recount:' + instanceEventsApi.countConsumers('test')
		reactorConfigPostProcessor.scanServices(applicationContext, TestService)
		log.info 'final count:' + instanceEventsApi.countConsumers('test')

		//tasks test: Book.async.list()
		//task{
			render 'test'
		//}
	}

}
