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
import reactor.event.dispatch.SynchronousDispatcher

includes = ['default']

doWithReactor = {
	reactor(EventsApi.GRAILS_REACTOR){
		on('someTopic'){
			reply 'test'
		}
	}
	reactor('someGormReactor'){
		dispatcher = new SynchronousDispatcher()
		ext 'gorm', true

		stream{
			consume{
				log.info "Some gorm event is flowing with data $it.data"
			}.when(Throwable){
				log.error "Ow snap!", it
			}
		}
	}
}