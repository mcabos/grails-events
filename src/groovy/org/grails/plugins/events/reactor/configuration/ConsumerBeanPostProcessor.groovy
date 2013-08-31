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
class ConsumerBeanPostProcessor implements BeanPostProcessor, Ordered {
	private static final Logger LOG = LoggerFactory.getLogger(ConsumerBeanPostProcessor)
	public static final ReflectionUtils.MethodFilter CONSUMER_METHOD_FILTER = new ReflectionUtils.MethodFilter() {
		@Override
		boolean matches(Method method) {
			AnnotationUtils.findAnnotation method, Selector
		}
	}

	private final ConversionService conversionService


	EventsApi eventsApi

	ConsumerBeanPostProcessor() {
		this.conversionService = new DefaultFormattingConversionService()
	}

	@Autowired(required=false)
	ConsumerBeanPostProcessor(ConversionService conversionService) {
		this.conversionService = conversionService
	}

	@Override
	int getOrder() {
		LOWEST_PRECEDENCE
	}

	@Override
	def postProcessBeforeInitialization(bean,
	                                    String beanName) throws BeansException {
		bean
	}

	@Override
	def postProcessAfterInitialization(final bean,
	                                   String beanName) throws BeansException {

		initBean bean, C.findHandlerMethods(bean.getClass(), CONSUMER_METHOD_FILTER)
	}

	def initBean(final bean, Set<Method> methods) {
		if(!methods)
			return bean

		Consumer consumer
		Reactor reactor
		Selector selectorAnno
		ReplyTo replyToAnno
		S selector

		def scanAnnotation = { Method method ->
			selectorAnno = AnnotationUtils.findAnnotation(method, Selector)
			replyToAnno = AnnotationUtils.findAnnotation(method, ReplyTo)
			reactor = fetchReactor(selectorAnno)
			selector = fetchSelector(selectorAnno, method)
		}

		//clean existing consumers for this service
		for (final Method method : methods) {
			scanAnnotation method

			for (registration in reactor.consumerRegistry.select(selector.object)) {
				consumer = registration.object
				if ((consumer.getClass() == ServiceConsumer &&
						((ServiceConsumer) consumer).handler.bean.getClass() == bean.getClass()) ||
						(consumer.getClass() == ReplyToServiceConsumer &&
								((ReplyToServiceConsumer) consumer).handler.bean.getClass()	== bean.getClass()))
					registration.cancel()
			}
		}

		for (final Method method : methods) {
			scanAnnotation method

			//register [replyTo]consumer
			String replyTo = replyToAnno?.value()
			Invoker handler = new Invoker(method, bean)
			consumer = replyTo ?
					new ReplyToServiceConsumer(reactor, replyTo, handler) :
					new ServiceConsumer(handler)

			if (LOG.debugEnabled) {
				LOG.debug("Attaching Consumer to Reactor[" + reactor + "] using Selector[" + selector + "]")
			}

			if (!selector) {
				reactor.on(consumer)
			} else {
				reactor.on(selector, consumer)
			}
		}
		bean
	}

	private Reactor fetchReactor(Selector selectorAnno) {
		selectorAnno.reactor() == 'reactor' ?
				eventsApi.appReactor :
				eventsApi.groovyEnvironment[selectorAnno.reactor()]
	}

	private S fetchSelector(Selector selectorAnno, Method method) {
		String sel = selectorAnno.value() ?: method.name
		try {
			switch (selectorAnno.type()) {
				case SelectorType.OBJECT:
					return object(sel)
				case SelectorType.REGEX:
					return regex(sel)
				case SelectorType.URI:
					return uri(sel)
				case SelectorType.TYPE:
					try {
						return type(Class.forName(sel))
					} catch (ClassNotFoundException e) {
						throw new IllegalArgumentException(e.message, e)
					}
			}
		} catch (EvaluationException e) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("Creating ObjectSelector for '" + sel + "' due to " + e.message, e)
			}
			return object(sel)
		}
	}

	private final static class ReplyToServiceConsumer implements Consumer<Event> {

		final Reactor reactor
		final Object replyToKey
		final Invoker handler

		ReplyToServiceConsumer(Reactor reactor, Object replyToKey, Invoker handler) {
			this.reactor = reactor
			this.replyToKey = replyToKey
			this.handler = handler
		}

		@Override
		void accept(Event ev) {
			Object result = handler.apply(ev)
			reactor.notify replyToKey, Event.wrap(result)
		}
	}

	private final static class ServiceConsumer implements Consumer<Event> {
		final Invoker handler

		ServiceConsumer(Invoker handler) {
			this.handler = handler
		}

		@Override
		void accept(Event ev) {
			handler.apply(ev)
		}
	}

	private final class Invoker implements Function<Event, Object> {

		final Method method

		final Object bean

		Invoker(Method method, Object bean) {
			this.method = method
			this.bean = bean
		}
		Class<?>[] argTypes = method.parameterTypes

		@Override
		def apply(Event ev) {
			if (argTypes.length == 0) {
				if (LOG.debugEnabled) {
					LOG.debug("Invoking method[" + method + "] on " + bean + " using " + ev)
				}
				return ReflectionUtils.invokeMethod(method, bean)
			}

			if (argTypes.length > 1) {
				throw new IllegalStateException("Multiple parameters not yet supported.")
			}

			if (null == ev.data || argTypes[0].isAssignableFrom(ev.data.getClass())) {
				if (LOG.debugEnabled) {
					LOG.debug("Invoking method[" + method + "] on " + bean + " using " + ev.data)
				}
				return ReflectionUtils.invokeMethod(method, bean, ev.data)
			}

			if (!argTypes[0].isAssignableFrom(ev.getClass())
					&& conversionService.canConvert(ev.getClass(), argTypes[0])) {
				ReflectionUtils.invokeMethod(method, bean, conversionService.convert(ev, argTypes[0]))
			}

			if (conversionService.canConvert(ev.data.getClass(), argTypes[0])) {
				Object convertedObj = conversionService.convert(ev.data, argTypes[0])
				if (LOG.debugEnabled) {
					LOG.debug("Invoking method[" + method + "] on " + bean + " using " + convertedObj)
				}
				return ReflectionUtils.invokeMethod(method, bean, convertedObj)
			}

			throw new IllegalArgumentException("Cannot invoke method " + method + " passing parameter " + ev.data)
		}
	}
}
