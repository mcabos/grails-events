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
package org.grails.plugins.events.reactor.api

import groovy.transform.CompileStatic
import reactor.core.Environment
import reactor.core.Reactor
import reactor.core.composable.Deferred
import reactor.core.composable.Promise
import reactor.core.composable.Stream
import reactor.core.composable.spec.Streams
import reactor.core.spec.Reactors
import reactor.event.Event
import reactor.event.registry.Registration
import reactor.event.selector.Selectors
import reactor.event.support.EventConsumer
import reactor.function.Consumer
/**
 * @author Stephane Maldini
 */
@CompileStatic
class EventsApi {

	Environment environment
	Reactor appReactor
	final Map<String, Reactor> reactorRegistry = [:]

	Reactor newReactor(instance) {
		Reactors.reactor().env(environment).get()
	}

	Reactor newReactor(instance, String namespace) {
		def r = Reactors.reactor().env(environment).get()
		reactorRegistry[namespace] = r
		r
	}

	Stream<?> withStream(instance, Deferred<?, Stream<?>> s,
	                     @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = WithStream) Closure c) {
		def withStream = new WithStream(s)
		c.delegate = withStream
		c.call()
		s.compose()
	}


	Stream<?> withStream(instance, @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			value = WithStream) Closure c) {

		def deferred = Streams.<?> defer().env(environment).get()
		def stream = deferred.compose()
		stream.consume c

		def withStream = new WithStream(deferred)
		c.delegate = withStream
		c.call()

		stream
	}

	void event(instance, key, Closure callback) {
		event(instance, key, null, null, callback)
	}

	void event(instance, key, data, Closure callback) {
		event(instance, key, data, null, callback)
	}

	void event(instance, Map args, Closure callback = null) {
		def namespace = args.remove('for') ?: args.remove('namespace')
		event(instance,
				args.remove('key'),
				args.remove('data'),
				(String) namespace,
				(Map) args.remove('params') ?: args,
				streamCallback(callback))
	}

	void event(instance, key, data = null, Map params = null, Closure callback = null) {
		event(instance,
				key,
				data,
				null,
				params,
				streamCallback(callback)
		)
	}

	private Deferred<?, Stream<?>> streamCallback(Closure<?> callback){
		Deferred<?, Stream<?>> s = null

		if (callback) {
			s = Streams.<?> defer().env(environment).get()
			s.compose().consume callback
		}

		s
	}

	protected void event(instance, key, data, String ns, Map params, Consumer<?> deferred) {

		final ev = new Event(params ? new Event.Headers(params) : null, data)

		final reactor = ns ? reactorRegistry[ns] : appReactor

		if (deferred) {
			final replyTo = Selectors.$()
			ev.setReplyTo(replyTo.t2)

			if (deferred instanceof Deferred<Event, Stream<Event>>)
				reactor.on replyTo.t1, deferred
			else
				reactor.on replyTo.t1, new EventConsumer(deferred)

			reactor.send key, ev
		} else {
			reactor.notify key, ev
		}

	}

	Registration<Consumer> on(instance, String key, Closure callback) {
		appReactor.on(key, callback)
	}

	Registration<Consumer> on(instance, String namespace, String key, Closure callback) {
		reactorRegistry[namespace].on(key, callback)
	}

	boolean removeListeners(instance, selector) {
		appReactor.consumerRegistry.unregister(selector)
	}

	int countListeners(instance, selector) {
		appReactor.consumerRegistry.select(selector).size()
	}

	private class WithStream extends EventsApi {
		Deferred<?, Stream<?>> deferred

		WithStream(Deferred<?, Stream<?>> deferred) {
			this.deferred = deferred
		}

		@Override
		protected void event(instance, key, data, String ns, Map params, Consumer<?> deferred) {
			super.event(instance, key, data, ns, params, deferred)
		}
	}
}
