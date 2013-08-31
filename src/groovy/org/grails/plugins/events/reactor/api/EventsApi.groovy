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
import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.grails.plugins.events.reactor.configuration.EventsArtefactHandler
import reactor.core.Environment
import reactor.core.Reactor
import reactor.core.composable.Deferred
import reactor.core.composable.Promise
import reactor.core.composable.Stream
import reactor.core.composable.spec.Streams
import reactor.core.spec.Reactors
import reactor.event.Event
import reactor.event.registry.Registration
import reactor.event.selector.Selector
import reactor.event.selector.Selectors
import reactor.event.support.EventConsumer
import reactor.function.Consumer
import reactor.groovy.config.GroovyEnvironment
import reactor.groovy.support.ClosureEventConsumer
/**
 * @author Stephane Maldini
 */
@CompileStatic
class EventsApi {

	private static final log = Logger.getLogger(EventsApi)

	static final String GRAILS_REACTOR = 'grailsReactor'

	GroovyEnvironment groovyEnvironment
	Reactor appReactor

	Reactor newReactor(instance) {
		Reactors.reactor().env(groovyEnvironment.environment()).get()
	}

	Reactor newReactor(instance, String namespace) {
		def r = Reactors.reactor().env(groovyEnvironment.environment()).get()
		groovyEnvironment[namespace] = r
		r
	}

	private WithStream newStream(Deferred d, Closure c){
		def withStream = new WithStream(d)
		withStream.appReactor = appReactor
		withStream.groovyEnvironment = groovyEnvironment
		c.delegate = withStream
		c.resolveStrategy = Closure.DELEGATE_FIRST
		c.call()
		withStream
	}

	Stream<?> withStream(instance, Deferred<?, Stream<?>> s,
	                     @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = WithStream) Closure c) {
		newStream(s, c)
		s.compose()
	}


	Stream<?> withStream(instance, @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			value = WithStream) Closure c) {

		def deferred = Streams.<?> defer().env(groovyEnvironment.environment()).get()
		def stream = deferred.compose()

		newStream(deferred, c)
		stream
	}

	void event(instance, key,
	           @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			           value = ClosureEventConsumer)
	           Closure callback) {
		event(instance, key, null, null, callback)
	}

	void event(instance, key, data,
	           @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			           value = ClosureEventConsumer)
	           Closure callback) {
		event(instance, key, data, null, callback)
	}

	void event(instance, Map args,
	           @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			           value = ClosureEventConsumer)
	           Closure callback = null) {
		def namespace = args.remove('for') ?: args.remove('namespace')
		event(instance,
				args.remove('key'),
				args.remove('data'),
				(String) namespace,
				(Map) args.remove('params') ?: args,
				new ClosureEventConsumer(callback))
	}

	void event(instance, key, data = null, Map params = null,
	           @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			           value = ClosureEventConsumer)
	           Closure callback = null) {
		event(instance,
				key,
				data,
				null,
				params,
				callback ? new ClosureEventConsumer(callback) : null
		)
	}


	protected void event(instance, key, data, String ns, Map params, Consumer<Event> deferred) {

		final Event ev = Event.class.isAssignableFrom(data?.class) ? (Event) data :
				new Event(params ? new Event.Headers(params) : null, data)

		final reactor = ns ? groovyEnvironment[ns] : appReactor

		if (deferred) {
			final replyTo = Selectors.$()
			ev.setReplyTo(replyTo.t2)

			reactor.on replyTo.t1, deferred
		}

		if (ev.replyTo)
			reactor.send key, ev
		else
			reactor.notify key, ev
	}

	Registration<Consumer> on(instance, key, @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			value = ClosureEventConsumer.ReplyDecorator) Closure callback) {
		_on(instance, appReactor, key, callback)
	}

	Registration<Consumer> on(instance, String namespace, key, @DelegatesTo(strategy = Closure.DELEGATE_FIRST,
			value = ClosureEventConsumer.ReplyDecorator) Closure callback) {
		_on(instance, groovyEnvironment[namespace], key, callback)
	}

	private Registration<Consumer> _on(instance, Reactor reactor, key, Closure callback) {
		if (key instanceof String)
			reactor.on key, callback
		else if (key instanceof Selector)
			reactor.on key, callback
		else
			reactor.on Selectors.$(key), callback
	}

	boolean removeListeners(instance, selector) {
		appReactor.consumerRegistry.unregister(selector)
	}

	int countListeners(instance, selector) {
		appReactor.consumerRegistry.select(selector).size()
	}

	private class WithStream extends EventsApi {
		Deferred deferred

		WithStream(Deferred<?, Stream<?>> deferred) {
			this.deferred = deferred
		}

		@Override
		protected void event(instance, key, data, String ns, Map params, Consumer<Event> _deferred) {
			super.event(instance, key, data, ns, params, new Consumer<Event>() {
				@Override
				void accept(Event o) {
					if(_deferred)
						_deferred << o

					deferred << o.data
				}
			})
		}
	}
}
