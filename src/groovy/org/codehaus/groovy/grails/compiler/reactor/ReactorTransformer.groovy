/*
 * Copyright (c) 2011-2013 GoPivotal, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.codehaus.groovy.grails.compiler.reactor

import grails.web.controllers.ControllerMethod
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.grails.commons.BootstrapArtefactHandler
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.ServiceArtefactHandler
import org.codehaus.groovy.grails.compiler.injection.AbstractGrailsArtefactTransformer
import org.codehaus.groovy.grails.compiler.injection.AstTransformer
import org.codehaus.groovy.grails.compiler.injection.GrailsASTUtils
import org.codehaus.groovy.grails.io.support.GrailsResourceUtils
import org.grails.plugins.events.reactor.api.EventsApi
/**
 * @author Stephane Maldini
 */
@AstTransformer
@CompileStatic
class ReactorTransformer extends AbstractGrailsArtefactTransformer {

	static private CONTROLLER_PATTERN =
			/.+\/$GrailsResourceUtils.GRAILS_APP_DIR\/controllers\/(.+)Controller\.groovy/
	static private  SERVICE_PATTERN =
			/.+\/$GrailsResourceUtils.GRAILS_APP_DIR\/conf\/(.*)BootStrap\.groovy/
	static private  BOOTSTRAP_PATTERN =
			/.+\/$GrailsResourceUtils.GRAILS_APP_DIR\/services\/(.+)Service\.groovy/
	static private  DOMAIN_PATTERN =
			/.+\/$GrailsResourceUtils.GRAILS_APP_DIR\/domain\/(.+)\.groovy/

	@Override
	Class<?> getInstanceImplementation() {
		return EventsApi.class
	}

	@Override
	Class<?> getStaticImplementation() {
		null  // No static api
	}

	boolean shouldInject(URL url) {
		url && (CONTROLLER_PATTERN==~url.file || SERVICE_PATTERN==~url.file || BOOTSTRAP_PATTERN==~url.file ||
				DOMAIN_PATTERN==~url.file)
	}

	@Override
	protected void performInjectionInternal(String apiInstanceProperty,
	                                        SourceUnit source, ClassNode classNode) {
		super.performInjectionInternal(apiInstanceProperty, source, classNode)

		def staticInit = classNode.getDeclaredMethods("<clinit>")
		if (staticInit) {
			def methodNode = staticInit.get(0)
			GrailsASTUtils.wrapMethodBodyInTryCatchDebugStatements(methodNode)
		}
	}

	@Override
	protected AnnotationNode getMarkerAnnotation() {
		new AnnotationNode(new ClassNode(ControllerMethod.class).plainNodeReference)
	}

	@Override
	String[] getArtefactTypes() {
		[ControllerArtefactHandler.TYPE, DomainClassArtefactHandler.TYPE, BootstrapArtefactHandler.TYPE,
				ServiceArtefactHandler.TYPE] as String[]
	}

	@Override
	void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
		super.performInjection(source, context, classNode)
	}

	@Override
	void performInjection(SourceUnit source, ClassNode classNode) {
		super.performInjection(source, classNode)
	}

}
