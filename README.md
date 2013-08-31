grails-events
=============

Grails Events plugin integrates [Reactor](http://reactor.github.io/reactor/) with Grails.
This plugin is the natural evolution of [Platform-Core](https://github.com/grails-plugins/grails-platform-core) plugin. Migration is possible and encouraged as there is a reactor aternative for most of the Platform-Core API.

To install it, add this in your BuildConfig.groovy *dependencies* block:
```groovy
dependencies {
		compile 'org.grails.plugins:events:1.0.0.BUILD-SNAPSHOT'
}
```

Grails Events plugin statically injects [EventsAPI](https://github.com/reactor/grails-events/blob/master/src/groovy/org/grails/plugins/events/reactor/api/EventsApi.groovy) methods in Grails Domains, Controllers, Services and Bootstrap artefacts.
Here is basic sample of what you can do from a Controller:

```groovy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import static grails.async.Promises.*

class TestController {

  //internal bean to force service consumers registration
	def reactorConfigPostProcessor

	def onTest() {
	  //injected method to consume events
		on('test') {
		  //asynchronous reply (dispatcher based) to the event replyTo key
			reply it
		}
		render ' on test '
	}

	def test() {
	  //notify test key with data 1 and listen for replies if any
		event('test', 1) {
			log.info 'eventCallback: ' + it
		}

    //register a Reactor Stream for event replies manipulation
		withStream {
		  //map notation to notify event 'test'
			event(key: 'test', data: 1) {
				log.info 'eventCallback2: ' + it
			}
			//withStream returns a Reactor Stream, reactor-groovy extensions allows consuming using '<<' with Closure
		} << {
		  // consume replies
			log.info 'streamCallback:  ' + it
		} << {
		  // consume replies
			log.info 'streamCallback2:  ' + it
		}

    // Prepare a latch for the next event '/someUri'
		def latch = new CountDownLatch(1)
		event(key: '/someUri', data: 1) {
			log.info 'uriCallback: ' + it
			//unlock the latch
			latch.countDown()
		}

		latch.await(5, TimeUnit.SECONDS)

    //count consumers for event key test
		log.info 'count:' + countConsumers('test')
		log.info 'remove test consumers:' + removeConsumers('test')
		log.info 'remove /test consumers:' + removeConsumers('/someUri')
		log.info 'recount:' + countConsumers('test')
		
		//since we removed consumers, register them again:
		reactorConfigPostProcessor.scanServices(applicationContext, BookService)
		log.info 'final count:' + countConsumers('test')

    //suspend the request using servlet 3 / NIO grails 2.3 async features
    //backed by Reactor Promise
		task {
			render 'test'
		}
	}
}
```

Grails Events let you use Spring Beans as Reactor Consumers. There is especically support for Service artefacts as they will support hot reloading.
```groovy
import groovy.transform.CompileStatic
import reactor.events.Event
import reactor.spring.annotation.ReplyTo
import reactor.spring.annotation.Selector
import reactor.spring.annotation.SelectorType

@CompileStatic
class TestService {

  //consumes 'test' events
	@Selector
	void test(){
		log.info 'test 1'
	}

  //consumes 'test' events
	@Selector ('test')
	void test2(Event<Integer> ev){
		log.info 'test 2:'+ev.data
	}
	
	//consumes 'test' events and replies the returned value to event replyTo.key
	@Selector ('test')
	@ReplyTo
	String test4(data){
		log.info 'test 4 : '+data
		'reply from 4'
	}

  //consumes every events matching the URI template '/**', e.g. '/test'
  //replies to the event replyTo.key
	@Selector (value='/**', type = SelectorType.URI)
	@ReplyTo
	String test5(){
		log.info 'test 5'
		'reply from 5'
	}
}

```

Now you can create a grails reactor environment with the Events artefacts, e.g. grails-app/conf/SomeEvents.groovy:
```groovy
doWithReactor = {
		environment {
		  //statically includes another GroovyEnvironment
		  include(AnotherEnvironment)
		  
		  //define default dispatcher id
			defaultDispatcher = "grailsDispatcher"

      //define a RingBuffer Dispatcher identified by 'grailsDispatcher'
			dispatcher('grailsDispatcher') {
				type = DispatcherType.RING_BUFFER
				backlog = 512
			}
			
			//define a specific reactor identified by 'someRandomReactor'
			//will use default dispatcher as not defined explicitely in reactor closure
			//Grails-Events comes with a default reactor called 'grailsReactor' available in DefaultEvents and overridable
			reactor('someRandomReactor') {
			  //register inline consumers
			  on('testEvent'){
			     //println stuff
			  }
			 reactor('another'){
			   //inerit testEvent consumer
			 }
			}
	}
}
```
