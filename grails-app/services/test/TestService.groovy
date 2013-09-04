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

import groovy.transform.CompileStatic
import reactor.spring.annotation.ReplyTo
import reactor.spring.annotation.Selector
import reactor.spring.annotation.SelectorType

/**
 * @author Stephane Maldini
 */
@CompileStatic
class TestService {

	@Selector
	void afterInsert(Book b){
		log.info 'test 1:'+b
	}

	@Selector(reactor = 'someGormReactor')
	void beforeValidate(Book b){
		log.info 'test 1-before:'+b

		assert b.title == 'lol'
	}

	@Selector ('test')
	void test2() throws Exception{
		log.info 'test 2'
		throw new Exception()
	}

	@Selector ('test')
	@ReplyTo
	void test3(){
		Book.get(1)?.title = 'test'
		log.info 'test 3:'+Book.list().title
	}

	@Selector ('test')
	@ReplyTo
	String test4(){
		log.info 'test 4'
		'reply from 4'
	}

	@Selector (value='/**', type = SelectorType.URI)
	@ReplyTo
	String test5(){
		log.info 'test 5'
		'reply from 5'
	}
}
