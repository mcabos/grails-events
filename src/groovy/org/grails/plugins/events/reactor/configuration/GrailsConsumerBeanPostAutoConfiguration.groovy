package org.grails.plugins.events.reactor.configuration

import groovy.transform.CompileStatic
import org.grails.plugins.events.reactor.api.EventsApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.convert.ConversionService
import reactor.core.Reactor
import reactor.function.Consumer
import reactor.spring.annotation.ReplyTo
import reactor.spring.annotation.Selector
import reactor.spring.beans.factory.config.ConsumerBeanAutoConfiguration

import java.lang.reflect.Method
/**
 * @author Stephane Maldini
 */
@CompileStatic
class GrailsConsumerBeanPostAutoConfiguration extends ConsumerBeanAutoConfiguration{

	EventsApi eventsApi

	GrailsConsumerBeanPostAutoConfiguration() {
	}

	@Override
	void wireBean(bean, Set<Method> methods) {
		//clean existing consumers for this service
		Consumer consumer
		Reactor reactor
		Selector selectorAnno
		reactor.event.selector.Selector selector

		for (final Method method : methods) {
			//scanAnnotation method
			selectorAnno = AnnotationUtils.findAnnotation(method, Selector.class);
			reactor = fetchObservable(selectorAnno, bean)
			selector = fetchSelector(selectorAnno, bean, method);

			for (registration in reactor.consumerRegistry.select(selector.object)) {
				consumer = registration.object
				if ((consumer.getClass() == ConsumerBeanAutoConfiguration.ServiceConsumer &&
						((ConsumerBeanAutoConfiguration.ServiceConsumer) consumer).handler.bean.getClass() == bean.getClass()) ||
						(consumer.getClass() == ConsumerBeanAutoConfiguration.ReplyToServiceConsumer &&
								((ConsumerBeanAutoConfiguration.ReplyToServiceConsumer) consumer).handler.bean.getClass()	== bean.getClass()))
					registration.cancel()
			}
		}

		super.wireBean(bean, methods)
	}

	@Override
	protected Reactor fetchObservable(Selector selectorAnno, Object bean) {
		selectorAnno.reactor() == 'reactor' ?
				eventsApi.appReactor :
				eventsApi.groovyEnvironment[selectorAnno.reactor()]
	}

	@Override
	protected Object parseSelector(Selector selector, Object bean, Method method) {
		selector.value() ?: method.name
	}

	@Override
	protected Object parseReplyTo(ReplyTo selector, Object bean) {
		selector.value() ?: null
	}

}
