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

import reactor.spring.annotation.Selector

/**
 * @author Stephane Maldini
 */
class TestService {

	@Selector
	void test(){
		println '1'
	}
	@Selector ('test')
	void test2(){
		println Thread.currentThread()
	}
	@Selector ('test')
	void test3(){
		println 'ss'
	}

	@Selector ('test')
	String test4(){
		println 'ww'
		'wss'
	}
}
