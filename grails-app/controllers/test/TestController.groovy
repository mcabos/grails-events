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

import reactor.function.Consumer

/**
 * @author Stephane Maldini
 */
class TestController {

	def instanceEventsApi

	def onTest() {
		instanceEventsApi.on(this, 'test') {
			reply it
		}
		render ' on test '
	}

	def test() {
		//tasks test: Book.async.list()
		instanceEventsApi.event ( this, 'test', 1 ) {
			println it
		}

		instanceEventsApi.withStream(this) {
			event(this, 'test', 1) {
				println '1x '+ it
			}
		} consume ({
			println '2x ' + it
		} as Consumer)



		render 'test'
	}

}
