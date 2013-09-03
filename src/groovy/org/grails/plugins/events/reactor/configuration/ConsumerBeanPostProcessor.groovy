package org.grails.plugins.events.reactor.configuration

import groovy.transform.CompileStatic
import org.grails.plugins.events.reactor.api.EventsApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanPostProcessor
import reactor.spring.beans.factory.config.ConsumerBeanPostProcessor as C
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.convert.ConversionService
import org.springframework.expression.EvaluationException
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.util.Assert
import org.springframework.util.ReflectionUtils
import reactor.core.Reactor
import reactor.event.Event
import reactor.function.Consumer
import reactor.function.Function
import reactor.spring.annotation.ReplyTo
import reactor.spring.annotation.Selector
import reactor.spring.annotation.SelectorType
import reactor.event.selector.Selector as S

import java.lang.reflect.Method

import static reactor.event.selector.Selectors.*
/**
 * @author Stephane Maldini
 */
@CompileStatic
class ConsumerBeanPostProcessor extends reactor.spring.beans.factory.config.ConsumerBeanPostProcessor {

	EventsApi eventsApi

	ConsumerBeanPostProcessor() {
		super()
	}

	@Override
	Object initBean(Object bean, Set<Method> methods) {
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
				if ((consumer.getClass() == ServiceConsumer &&
						((ServiceConsumer) consumer).handler.bean.getClass() == bean.getClass()) ||
						(consumer.getClass() == ReplyToServiceConsumer &&
								((ReplyToServiceConsumer) consumer).handler.bean.getClass()	== bean.getClass()))
					registration.cancel()
			}
		}

		super.initBean(bean, methods)
	}

	@Autowired(required = false)
	ConsumerBeanPostProcessor(ConversionService conversionService) {
		super(conversionService)
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
