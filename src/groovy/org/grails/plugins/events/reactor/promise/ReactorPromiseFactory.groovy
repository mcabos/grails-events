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
package org.grails.plugins.events.reactor.promise

import grails.async.Promise
import grails.async.PromiseList
import groovy.transform.CompileStatic
import org.grails.async.factory.AbstractPromiseFactory
import reactor.core.Environment
import reactor.core.composable.spec.Promises

/**
 * Reactor Implementation of {@link grails.async.PromiseFactory} interface
 *
 * @author Stephane Maldini
 * @since 2.3
 */
@CompileStatic
class ReactorPromiseFactory extends AbstractPromiseFactory {

	Environment grailsEnvironment = new Environment()

	ReactorPromiseFactory() {
		super()
	}

	@Override
	def <T> Promise<T> createBoundPromise(T value) {
		final variable = Promises.success(value).env(grailsEnvironment).get()
		return new ReactorPromise<T>(variable)
	}


	@Override
	def <T> Promise<T> createPromise(Closure<T>... closures) {
		if (closures.length == 1) {
			final callable = closures[0]
			return new ReactorPromise<T>(applyDecorators(callable, null), grailsEnvironment)
		}
		def promiseList = new PromiseList()
		for (p in closures) {
			promiseList << applyDecorators(p, null)
		}

		promiseList
	}

	@Override
	def <T> List<T> waitAll(List<Promise<T>> promises) {
		final List<reactor.core.composable.Promise<T>> _promises = promises.collect {
			ReactorPromise it -> it.internalPromise
		}
		(List<T>) Promises.when(_promises).await()
	}

	@Override
	def <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
		def result = Promises.when(
				promises.collect { ReactorPromise it -> it.internalPromise }
		).onError(callable)

		new ReactorPromise<List<T>>(result)
	}

	@Override
	def <T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<?> callable) {
		def result = Promises.when(
				promises.collect { ReactorPromise it ->  it.internalPromise }
		).onSuccess(callable)

		new ReactorPromise<List<T>>(result)
	}
}